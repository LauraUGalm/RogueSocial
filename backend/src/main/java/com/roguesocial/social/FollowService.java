package com.roguesocial.social;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import com.roguesocial.user.User;
import com.roguesocial.user.UserRepository;

/** Following and unfollowing. Both are safe to repeat. */
@Service
public class FollowService {

    private final MongoTemplate mongo;
    private final SocialIndexes indexes;
    private final UserRepository users;
    private final BlockService blocks;

    public FollowService(MongoTemplate mongo, SocialIndexes indexes, UserRepository users, BlockService blocks) {
        this.mongo = mongo;
        this.indexes = indexes;
        this.users = users;
        this.blocks = blocks;
    }

    public void follow(String username, String target) {
        indexes.ensure();
        User me = user(username);
        User them = user(target);
        if (me.userId() == them.userId()) {
            throw new IllegalArgumentException("You can't follow yourself");
        }
        blocks.checkNotBlocked(me, them);
        try {
            mongo.insert(new Follow(null, me.userId(), them.userId(),
                    Instant.now().truncatedTo(ChronoUnit.MILLIS)));
        } catch (DuplicateKeyException alreadyFollowing) {
            // The unique index said no. Following twice is the same as following once.
        }
    }

    public void unfollow(String username, String target) {
        indexes.ensure();
        User me = user(username);
        User them = user(target);
        mongo.remove(query(where("followerId").is(me.userId()).and("followeeId").is(them.userId())),
                Follow.class);
    }

    /** The user IDs this player follows. */
    List<Long> following(long userId) {
        indexes.ensure();
        return mongo.find(query(where("followerId").is(userId)), Follow.class).stream()
                .map(Follow::followeeId)
                .toList();
    }

    private User user(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("No user " + username));
    }
}
