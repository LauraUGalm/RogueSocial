package com.roguesocial.social;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.roguesocial.user.User;
import com.roguesocial.user.UserRepository;

/**
 * Posting, replying, editing, liking, and the feed.
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

    public PostService(MongoTemplate mongo, SocialIndexes indexes, UserRepository users, Purse purse,
            FollowService follows) {
        this.mongo = mongo;
        this.indexes = indexes;
        this.users = users;
        this.purse = purse;
        this.follows = follows;
    }

    /** A new post, or a reply when {@code replyTo} is a post's id. */
    public Posted post(String username, String text, String replyTo) {
        indexes.ensure();
        User author = user(username);
        int cost = costOf(text);
        if (replyTo != null && find(replyTo).replyTo() != null) {
            throw new IllegalArgumentException("Replies are one level deep: reply to the post instead");
        }

        purse.spendGold(author.username(), cost);
        Post saved;
        try {
            saved = mongo.insert(new Post(null, author.userId(), author.username(), text, cost, now(),
                    null, replyTo, 0, 0));
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
                post.paid() + extra, post.createdAt(), editedAt, post.replyTo(), post.likeCount(),
                post.replyCount()), extra);
    }

    public Post find(String postId) {
        indexes.ensure();
        Post post = ObjectId.isValid(postId) ? mongo.findById(postId, Post.class) : null;
        if (post == null) {
            throw new NoSuchElementException("No post " + postId);
        }
        return post;
    }

    /** Oldest first, so a conversation reads top to bottom. */
    public List<Post> replies(String postId) {
        find(postId);
        return mongo.find(query(where("replyTo").is(postId))
                .with(Sort.by(Direction.ASC, "_id"))
                .limit(MAX_REPLIES), Post.class);
    }

    /** Liking a post twice is the same as liking it once. Returns the post with its new count. */
    public Post like(String username, String postId) {
        indexes.ensure();
        User me = user(username);
        Post post = find(postId);
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
     * The top-level posts of everyone this player follows, and their own, newest first.
     *
     * <p>This is "fan-out on read": the feed is put together each time it is asked for. The
     * {@code authorId, _id} index lets Mongo jump to each followed person's newest posts and merge
     * them. For the next page, pass the id of the last post on this one as {@code before}. That
     * stays fast however far back you go, where skipping the first N posts gets slower as N grows.
     */
    public List<Post> feed(String username, String before) {
        indexes.ensure();
        User me = user(username);
        List<Long> authors = new ArrayList<>(follows.following(me.userId()));
        authors.add(me.userId());

        // replyTo "is null" also matches posts with no replyTo field. Spring leaves null fields
        // out of the document, so that's every top-level post.
        Criteria criteria = where("authorId").in(authors).and("replyTo").is(null);
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
