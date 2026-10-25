package com.roguesocial.social;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import com.roguesocial.user.User;
import com.roguesocial.user.UserRepository;

/**
 * Blocking and unblocking. A block ends any friendship or request and the follows both ways, and
 * while it lasts the two can't follow or friend each other or see each other's posts. Unblocking
 * removes only the block: what it ended stays ended.
 */
@Service
public class BlockService {

    private final MongoTemplate mongo;
    private final SocialIndexes indexes;
    private final UserRepository users;

    public BlockService(MongoTemplate mongo, SocialIndexes indexes, UserRepository users) {
        this.mongo = mongo;
        this.indexes = indexes;
        this.users = users;
    }

    public void block(String username, String target) {
        indexes.ensure();
        User me = user(username);
        User them = user(target);
        if (me.userId() == them.userId()) {
            throw new IllegalArgumentException("You can't block yourself");
        }
        // The block goes in first, so a follow or friend request arriving while the rest is
        // cleaned up is refused instead of slipping in after the clean-up.
        try {
            mongo.insert(new Block(null, me.userId(), them.userId(), Instant.now().truncatedTo(ChronoUnit.MILLIS)));
        } catch (DuplicateKeyException alreadyBlocked) {
            // Blocking twice is the same as blocking once; the clean-up below is safe to repeat.
        }
        mongo.remove(query(FriendService.pair(me.userId(), them.userId())), Friendship.class);
        mongo.remove(query(new Criteria().orOperator(
                where("followerId").is(me.userId()).and("followeeId").is(them.userId()),
                where("followerId").is(them.userId()).and("followeeId").is(me.userId()))), Follow.class);
    }

    public void unblock(String username, String target) {
        indexes.ensure();
        User me = user(username);
        User them = user(target);
        mongo.remove(query(where("blockerId").is(me.userId()).and("blockedId").is(them.userId())), Block.class);
    }

    /** The usernames this player has blocked, in alphabetical order. Only they see this. */
    public List<String> blocked(String username) {
        indexes.ensure();
        long me = user(username).userId();
        List<Long> ids = mongo.find(query(where("blockerId").is(me)), Block.class).stream()
                .map(Block::blockedId)
                .toList();
        Map<Long, String> names = users.usernames(ids);
        List<String> blocked = new ArrayList<>(ids.stream().map(names::get).filter(n -> n != null).toList());
        blocked.sort(String.CASE_INSENSITIVE_ORDER);
        return blocked;
    }

    /** Whether either has blocked the other. */
    boolean between(long a, long b) {
        indexes.ensure();
        return mongo.exists(query(new Criteria().orOperator(
                where("blockerId").is(a).and("blockedId").is(b),
                where("blockerId").is(b).and("blockedId").is(a))), Block.class);
    }

    /** Everyone this player has blocked or been blocked by: whose posts they shouldn't see. */
    Set<Long> hiddenFrom(long userId) {
        indexes.ensure();
        Set<Long> hidden = new HashSet<>();
        for (Block b : mongo.find(query(new Criteria().orOperator(
                where("blockerId").is(userId), where("blockedId").is(userId))), Block.class)) {
            hidden.add(b.blockerId() == userId ? b.blockedId() : b.blockerId());
        }
        return hidden;
    }

    /**
     * Refuses a follow or friend request across a block. The one who blocked is told why. The
     * one who was blocked is told there's no such player, the same as if they had left.
     */
    void checkNotBlocked(User me, User them) {
        if (mongo.exists(query(where("blockerId").is(me.userId()).and("blockedId").is(them.userId())), Block.class)) {
            throw new IllegalArgumentException("You've blocked " + them.username() + "; unblock them first");
        }
        if (mongo.exists(query(where("blockerId").is(them.userId()).and("blockedId").is(me.userId())), Block.class)) {
            throw new NoSuchElementException("No user " + them.username());
        }
    }

    private User user(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("No user " + username));
    }
}
