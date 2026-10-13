package com.roguesocial.social;

import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * The indexes the social collections need. Mongo has no schema file like {@code db/schema.sql};
 * collections appear on the first insert, and indexes are created from code.
 *
 * <p>They are created the first time the social side is used, not at startup, so the game still
 * starts when Mongo is down. Creating an index that already exists does nothing, so this is safe
 * on every start.
 */
@Component
class SocialIndexes {

    private final MongoTemplate mongo;
    private volatile boolean created;

    SocialIndexes(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    void ensure() {
        if (created) {
            return;
        }
        synchronized (this) {
            if (created) {
                return;
            }
            // Someone's posts, newest first: the feed runs this once per person followed.
            mongo.indexOps(Post.class).createIndex(
                    new Index().on("authorId", Direction.ASC).on("_id", Direction.DESC));
            // A post's replies, oldest first.
            mongo.indexOps(Post.class).createIndex(
                    new Index().on("replyTo", Direction.ASC).on("_id", Direction.ASC));
            // Who I follow. Unique, so following twice is impossible.
            mongo.indexOps(Follow.class).createIndex(
                    new Index().on("followerId", Direction.ASC).on("followeeId", Direction.ASC).unique());
            // Who follows me.
            mongo.indexOps(Follow.class).createIndex(new Index().on("followeeId", Direction.ASC));
            // One like per person per post.
            mongo.indexOps(Like.class).createIndex(
                    new Index().on("postId", Direction.ASC).on("userId", Direction.ASC).unique());
            created = true;
        }
    }
}
