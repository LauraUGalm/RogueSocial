package com.roguesocial.social;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.roguesocial.social.Friendship.Status;
import com.roguesocial.user.User;
import com.roguesocial.user.UserRepository;

/**
 * Friend requests and friendships. One player asks, the other accepts by asking back (or
 * declines), and either can end it. All of it is safe to repeat.
 */
@Service
public class FriendService {

    /**
     * A player's friends, the requests waiting on them, and the requests they sent, by username
     * in alphabetical order.
     */
    public record Friends(List<String> friends, List<String> incoming, List<String> outgoing) {
    }

    private final MongoTemplate mongo;
    private final SocialIndexes indexes;
    private final UserRepository users;
    private final BlockService blocks;

    public FriendService(MongoTemplate mongo, SocialIndexes indexes, UserRepository users, BlockService blocks) {
        this.mongo = mongo;
        this.indexes = indexes;
        this.users = users;
        this.blocks = blocks;
    }

    /**
     * Asks {@code target} to be friends. If they already asked, this accepts, so "Accept" and
     * "Add friend" are the same request.
     */
    public void request(String username, String target) {
        indexes.ensure();
        User me = user(username);
        User them = user(target);
        if (me.userId() == them.userId()) {
            throw new IllegalArgumentException("You can't be your own friend");
        }
        blocks.checkNotBlocked(me, them);
        if (accept(me.userId(), them.userId())) {
            return;
        }
        try {
            mongo.insert(new Friendship(null, Math.min(me.userId(), them.userId()),
                    Math.max(me.userId(), them.userId()), me.userId(), Status.PENDING, now(), null));
        } catch (DuplicateKeyException pairExists) {
            // Already friends, already asked, or they asked in the moment since accept() above
            // looked. Only the last needs anything done.
            accept(me.userId(), them.userId());
        }
    }

    /**
     * Ends whatever is between the two: unfriends, declines their request, or takes back yours.
     */
    public void end(String username, String target) {
        indexes.ensure();
        User me = user(username);
        User them = user(target);
        mongo.remove(query(pair(me.userId(), them.userId())), Friendship.class);
    }

    public Friends friends(String username) {
        indexes.ensure();
        long me = user(username).userId();
        List<Friendship> all = mine(me);
        Map<Long, String> names = users.usernames(all.stream().map(f -> f.other(me)).toList());

        List<String> friends = new ArrayList<>();
        List<String> incoming = new ArrayList<>();
        List<String> outgoing = new ArrayList<>();
        for (Friendship f : all) {
            String name = names.get(f.other(me));
            if (name == null) {
                continue; // a player who has since been removed from Postgres
            }
            (f.status() == Status.ACCEPTED ? friends : f.requesterId() == me ? outgoing : incoming).add(name);
        }
        for (List<String> list : List.of(friends, incoming, outgoing)) {
            list.sort(String.CASE_INSENSITIVE_ORDER);
        }
        return new Friends(friends, incoming, outgoing);
    }

    /** The user IDs of this player's friends, not counting requests. */
    List<Long> friendIds(long userId) {
        indexes.ensure();
        return mine(userId).stream()
                .filter(f -> f.status() == Status.ACCEPTED)
                .map(f -> f.other(userId))
                .toList();
    }

    public boolean areFriends(long a, long b) {
        indexes.ensure();
        return mongo.exists(query(pair(a, b).and("status").is(Status.ACCEPTED)), Friendship.class);
    }

    /**
     * Every friendship and request this player is in, on either side of the pair.
     *
     * <p>Mongo answers an {@code $or} by running each branch on its own index and merging the
     * results: the unique {@code lowId, highId} index for the first (it starts with
     * {@code lowId}), and the {@code highId} index for the second.
     */
    private List<Friendship> mine(long userId) {
        return mongo.find(query(new Criteria().orOperator(
                where("lowId").is(userId), where("highId").is(userId))), Friendship.class);
    }

    /**
     * Turns {@code requesterId}'s pending request into a friendship, in one step, so two
     * players accepting at once can't both do it. False if there was no such request.
     */
    private boolean accept(long me, long requesterId) {
        Friendship accepted = mongo.findAndModify(
                query(pair(me, requesterId).and("status").is(Status.PENDING).and("requesterId").is(requesterId)),
                new Update().set("status", Status.ACCEPTED).set("acceptedAt", now()),
                FindAndModifyOptions.options().returnNew(true), Friendship.class);
        return accepted != null;
    }

    static Criteria pair(long a, long b) {
        return where("lowId").is(Math.min(a, b)).and("highId").is(Math.max(a, b));
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private User user(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("No user " + username));
    }
}
