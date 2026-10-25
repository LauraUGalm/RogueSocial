package com.roguesocial.social;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.roguesocial.social.Post.Audience;
import com.roguesocial.user.User;
import com.roguesocial.user.UserRepository;

/**
 * Posting, replying, editing, deleting, liking, and the feed.
 *
 * <p>Gold lives in Postgres and posts live in Mongo, and no transaction spans both. So gold is
 * taken first, then the post is written, and if the write fails the gold is given back.
 *
 * <p>The counters on a post ({@code likeCount}, {@code replyCount}) are updated after the like or
 * reply is written, as a second step. If the server died between the two, a count would be off
 * by one. Mongo can wrap both in a transaction, but only on a replica set, not on the single
 * local server this runs on; for a like count, off by one now and then is acceptable.
 */
@Service
public class PostService {

    /** Posts per page of the feed. */
    static final int PAGE = 20;
    /** Replies shown under a post. There is no paging for replies yet. */
    static final int MAX_REPLIES = 100;

    /** A post as saved, and the gold it cost this time. */
    public record Posted(Post post, int goldSpent) {
    }

    private final MongoTemplate mongo;
    private final SocialIndexes indexes;
    private final UserRepository users;
    private final Purse purse;
    private final FollowService follows;
    private final FriendService friends;
    private final BlockService blocks;
    /** How long a new post waits before others see it. A minute, except in most tests. */
    private final Duration grace;

    public PostService(MongoTemplate mongo, SocialIndexes indexes, UserRepository users, Purse purse,
            FollowService follows, FriendService friends, BlockService blocks,
            @Value("${rogue.post-grace:PT1M}") Duration grace) {
        this.mongo = mongo;
        this.indexes = indexes;
        this.users = users;
        this.purse = purse;
        this.follows = follows;
        this.friends = friends;
        this.blocks = blocks;
        this.grace = grace;
    }

    /**
     * A new post, or a reply when {@code replyTo} is a post's id. A reply goes to its post's
     * audience whatever {@code audience} says; a post with none goes to followers.
     */
    public Posted post(String username, String text, String replyTo, Audience audience) {
        indexes.ensure();
        User author = user(username);
        int cost = costOf(text);
        if (audience == null) {
            audience = Audience.FOLLOWERS;
        }
        if (replyTo != null) {
            Post parent = find(replyTo);
            if (parent.replyTo() != null) {
                throw new IllegalArgumentException("Replies are one level deep: reply to the post instead");
            }
            checkCanSee(author, parent);
            notDeleted(parent);
            notLiveYet(parent);
            audience = parent.audience() == null ? Audience.FOLLOWERS : parent.audience();
        }

        purse.spendGold(author.username(), cost);
        Post saved;
        Instant now = now();
        try {
            saved = mongo.insert(new Post(null, author.userId(), author.username(), text, cost, now,
                    null, null, replyTo, 0, 0, audience, now.plus(grace)));
        } catch (RuntimeException e) {
            purse.refundGold(author.username(), cost);
            throw e;
        }
        if (replyTo != null) {
            mongo.updateFirst(byId(replyTo), new Update().inc("replyCount", 1), Post.class);
        }
        return new Posted(saved, cost);
    }

    /**
     * Replaces a post's text. It costs only the symbols that take it past what was already paid:
     * a post paid for at 100 can go to 80 and back to 100 for nothing, and to 120 for 20.
     */
    public Posted edit(String username, String postId, String text) {
        indexes.ensure();
        User author = user(username);
        Post post = find(postId);
        if (post.authorId() != author.userId()) {
            throw new NotYourPostException();
        }
        notDeleted(post);
        int extra = Math.max(0, costOf(text) - post.paid());
        if (extra > 0) {
            purse.spendGold(author.username(), extra);
        }

        // Only change the post if it is still the version read above. If someone edited it in
        // between (the same player in two tabs), nothing matches and the gold goes back.
        Instant editedAt = now();
        var result = mongo.updateFirst(
                query(where("_id").is(post.id()).and("text").is(post.text()).and("paid").is(post.paid())),
                new Update().set("text", text).set("paid", post.paid() + extra).set("editedAt", editedAt),
                Post.class);
        if (result.getMatchedCount() == 0) {
            if (extra > 0) {
                purse.refundGold(author.username(), extra);
            }
            throw new IllegalStateException("The post changed while you were editing it; try again");
        }
        return new Posted(new Post(post.id(), post.authorId(), post.authorName(), text,
                post.paid() + extra, post.createdAt(), editedAt, null, post.replyTo(), post.likeCount(),
                post.replyCount(), post.audience(), post.liveAt()), extra);
    }

    /**
     * Takes back your post in its first minute, before anyone else has seen it, and refunds all
     * the gold it cost, edits included. Nobody saw it, so nothing was said.
     *
     * <p>The check and the removal are one step: "remove it if it's still not live". So a post
     * going live at that moment either goes back in full or stays up, never half of each. No one
     * else can like it or reply to it before it's live, so there's nothing else to tidy up.
     *
     * @return the gold refunded
     */
    public int takeBack(String username, String postId) {
        indexes.ensure();
        User author = user(username);
        Post post = find(postId);
        if (post.authorId() != author.userId()) {
            throw new NotYourPostException();
        }
        // findAndRemove hands back the document as it was when removed, so an edit made since
        // the read above (which cost more) is refunded too.
        Post removed = mongo.findAndRemove(query(where("_id").is(post.id()).and("liveAt").gt(now())), Post.class);
        if (removed == null) {
            throw new IllegalStateException("It's already live; delete it instead");
        }
        purse.refundGold(author.username(), removed.paid());
        if (removed.replyTo() != null) {
            mongo.updateFirst(byId(removed.replyTo()), new Update().inc("replyCount", -1), Post.class);
        }
        return removed.paid();
    }

    /**
     * Deletes your post. No gold comes back: what was said was paid for.
     *
     * <p>A post nobody has replied to is removed, with its likes. One with replies becomes a
     * tombstone (its text unset, {@code deletedAt} set) so the replies, which other people paid
     * for, still have a post to sit under. Deleting the last reply under a tombstone removes it.
     *
     * @return the tombstone, or empty if the post is gone
     */
    public Optional<Post> delete(String username, String postId) {
        indexes.ensure();
        User author = user(username);
        Post post = find(postId);
        if (post.authorId() != author.userId()) {
            throw new NotYourPostException();
        }
        if (post.deletedAt() != null) {
            return Optional.of(post);
        }

        // "Remove it if replyCount is 0" is one step in Mongo, so a reply counted after find()
        // above still saves the post. A reply written but not yet counted is the gap (see the
        // class comment): its parent can go, and the reply is left where nothing shows it.
        long removed = mongo.remove(query(where("_id").is(post.id()).and("replyCount").is(0)), Post.class)
                .getDeletedCount();
        Post tombstone = null;
        if (removed == 0) {
            tombstone = mongo.findAndModify(byId(post.id()),
                    new Update().unset("text").set("deletedAt", now()).set("likeCount", 0),
                    FindAndModifyOptions.options().returnNew(true), Post.class);
        }
        // The postId, userId index finds these by its first field alone.
        mongo.remove(query(where("postId").is(post.id())), Like.class);
        if (removed == 1 && post.replyTo() != null) {
            replyRemoved(post.replyTo());
        }
        return Optional.ofNullable(tombstone);
    }

    /** One post, if this player may see it. */
    public Post find(String username, String postId) {
        Post post = find(postId);
        checkCanSee(user(username), post);
        return post;
    }

    /** Any post, whoever it is for. Only for code that checks who is asking itself. */
    Post find(String postId) {
        indexes.ensure();
        Post post = ObjectId.isValid(postId) ? mongo.findById(postId, Post.class) : null;
        if (post == null) {
            throw new NoSuchElementException("No post " + postId);
        }
        return post;
    }

    /**
     * Oldest first, so a conversation reads top to bottom. Leaves out replies from anyone this
     * player has blocked or been blocked by, and other people's replies still in their first
     * minute. The post's {@code replyCount} still counts them.
     */
    public List<Post> replies(String username, String postId) {
        find(username, postId);
        User me = user(username);
        Set<Long> hidden = blocks.hiddenFrom(me.userId());
        return mongo.find(query(where("replyTo").is(postId).and("authorId").nin(hidden)
                .orOperator(where("authorId").is(me.userId()), live()))
                .with(Sort.by(Direction.ASC, "_id"))
                .limit(MAX_REPLIES), Post.class);
    }

    /** Liking a post twice is the same as liking it once. Returns the post with its new count. */
    public Post like(String username, String postId) {
        indexes.ensure();
        User me = user(username);
        Post post = find(postId);
        checkCanSee(me, post);
        notDeleted(post);
        notLiveYet(post);
        try {
            mongo.insert(new Like(null, post.id(), me.userId(), now()));
        } catch (DuplicateKeyException alreadyLiked) {
            return post;
        }
        return addLikes(post.id(), 1);
    }

    public Post unlike(String username, String postId) {
        indexes.ensure();
        User me = user(username);
        Post post = find(postId);
        long removed = mongo.remove(query(where("postId").is(post.id()).and("userId").is(me.userId())),
                Like.class).getDeletedCount();
        return removed == 0 ? post : addLikes(post.id(), -1);
    }

    /**
     * Which of these posts this player has liked, so a page of posts can show a filled heart.
     *
     * <p>One query for the whole page, not one per post: likes with this {@code userId} whose
     * {@code postId} is {@code $in} the list, asking Mongo for just the distinct {@code postId}s.
     * The unique {@code postId, userId} index answers it, each id in the list becoming one quick
     * look-up in the index, and the like documents themselves are never read.
     */
    public Set<String> likedAmong(String username, Collection<String> postIds) {
        if (postIds.isEmpty()) {
            return Set.of();
        }
        indexes.ensure();
        User me = user(username);
        // The query side converts the hex strings to ObjectIds, because Like.postId says it is
        // one. Distinct results skip that mapping and come back as raw ObjectIds.
        Set<String> liked = new HashSet<>();
        mongo.findDistinct(query(where("userId").is(me.userId()).and("postId").in(postIds)),
                "postId", Like.class, ObjectId.class).forEach(id -> liked.add(id.toHexString()));
        return liked;
    }

    /**
     * The top-level posts of everyone this player follows or is friends with, and their own,
     * newest first: their own posts, followers posts from people they follow or are friends with,
     * and friends posts from their friends.
     *
     * <p>That is an {@code $or} of three branches. Mongo plans each branch on its own (each can
     * use the {@code authorId, _id} index) and merges them back into one newest-first list.
     * {@code audience $ne FRIENDS} also matches posts with no audience field, from before there
     * were audiences, so those stay followers posts with no migration.
     *
     * <p>This is "fan-out on read": the feed is put together each time it is asked for. The
     * {@code authorId, _id} index lets Mongo jump to each followed person's newest posts and merge
     * them. For the next page, pass the id of the last post on this one as {@code before}. That
     * stays fast however far back you go, where skipping the first N posts gets slower as N grows.
     */
    public List<Post> feed(String username, String before) {
        indexes.ensure();
        User me = user(username);
        List<Long> friendIds = friends.friendIds(me.userId());
        Set<Long> followed = new HashSet<>(follows.following(me.userId()));
        followed.addAll(friendIds);

        // replyTo "is null" also matches posts with no replyTo field. Spring leaves null fields
        // out of the document, so that's every top-level post.
        Criteria criteria = new Criteria().orOperator(
                where("authorId").is(me.userId()),
                where("authorId").in(followed).and("audience").ne(Audience.FRIENDS).andOperator(live()),
                where("authorId").in(friendIds).and("audience").is(Audience.FRIENDS).andOperator(live()))
                .and("replyTo").is(null);
        if (before != null) {
            if (!ObjectId.isValid(before)) {
                throw new IllegalArgumentException("before must be a post id");
            }
            criteria = criteria.and("_id").lt(new ObjectId(before));
        }
        return mongo.find(query(criteria).with(Sort.by(Direction.DESC, "_id")).limit(PAGE), Post.class);
    }

    private Post addLikes(String postId, int change) {
        return mongo.findAndModify(byId(postId), new Update().inc("likeCount", change),
                FindAndModifyOptions.options().returnNew(true), Post.class);
    }

    private void replyRemoved(String parentId) {
        mongo.updateFirst(byId(parentId), new Update().inc("replyCount", -1), Post.class);
        // A tombstone was only kept for its replies. Spring leaves null fields out of the
        // document, so "deletedAt exists" means "is a tombstone".
        mongo.remove(query(where("_id").is(parentId).and("deletedAt").exists(true).and("replyCount").is(0)),
                Post.class);
    }

    /**
     * A followers post, and its replies, are for the author's followers and friends; a friends
     * post only for their friends. The same rule as the feed, so having a post's id doesn't get a
     * stranger any further than their feed would. Nothing is shown across a block, either way:
     * not the post, and not a reply under someone else's post. Anyone who can't see it is told
     * there is no such post, so they can't learn that one exists.
     */
    private void checkCanSee(User viewer, Post post) {
        Post top = post.replyTo() == null ? post : find(post.replyTo());
        long me = viewer.userId();
        boolean hidden = top.authorId() != me && !friends.areFriends(me, top.authorId())
                && (top.friendsOnly() || !follows.isFollowing(me, top.authorId()));
        boolean waiting = (!top.isLive(now()) && top.authorId() != viewer.userId())
                || (!post.isLive(now()) && post.authorId() != viewer.userId());
        if (hidden || waiting || blocks.between(viewer.userId(), top.authorId())
                || blocks.between(viewer.userId(), post.authorId())) {
            throw new NoSuchElementException("No post " + post.id());
        }
    }

    /**
     * Posts that others can see: {@code liveAt} is not after now. "Not after" rather than "at or
     * before", because {@code $not} also matches posts with no {@code liveAt} at all, from before
     * the grace period, the same trick as {@code audience $ne FRIENDS}.
     */
    private static Criteria live() {
        return where("liveAt").not().gt(now());
    }

    /** Nobody, the author included, can like a post or reply to it in its first minute. */
    private static void notLiveYet(Post post) {
        if (!post.isLive(now())) {
            throw new IllegalArgumentException("That post isn't live yet");
        }
    }

    private static void notDeleted(Post post) {
        if (post.deletedAt() != null) {
            throw new IllegalArgumentException("That post was deleted");
        }
    }

    private static int costOf(String text) {
        int cost = text == null ? 0 : PostCost.of(text);
        if (cost == 0) {
            throw new IllegalArgumentException("A post needs at least one symbol that isn't a space");
        }
        return cost;
    }

    private static Query byId(String postId) {
        return query(where("_id").is(postId));
    }

    /** Mongo stores times to the millisecond, so round here and what's returned matches what's stored. */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private User user(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("No user " + username));
    }
}
