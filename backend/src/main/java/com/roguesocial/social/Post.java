package com.roguesocial.social;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * A post or a reply: one document in the {@code posts} collection.
 *
 * <p>Replies are their own documents rather than a list inside the post, because a list inside
 * would grow without limit and a document can't pass 16 MB. The author's name is copied in
 * from Postgres so the feed needs no second lookup to show who wrote each post.
 *
 * @param id         Mongo's ObjectId as hex. ObjectIds start with their creation time, so
 *                   sorting by id sorts by age, and the feed pages by id
 * @param authorId   {@code users.user_id} in Postgres
 * @param paid       the gold paid for this post so far, edits included. An edit only costs the
 *                   symbols that take it past this
 * @param editedAt   null until the first edit
 * @param deletedAt  null unless the post was deleted while it had replies. It is then a
 *                   tombstone: its text is gone, but it stays so the replies still have a parent
 * @param replyTo    the post this replies to, or null for a top-level post. Stored as an
 *                   ObjectId, not a string, so it matches the parent's {@code _id}
 * @param likeCount  kept on the post so showing a post doesn't count its likes
 * @param replyCount the same, for replies
 */
@Document("posts")
public record Post(
        @Id String id,
        long authorId,
        String authorName,
        String text,
        int paid,
        Instant createdAt,
        Instant editedAt,
        Instant deletedAt,
        @Field(targetType = FieldType.OBJECT_ID) String replyTo,
        int likeCount,
        int replyCount) {
}
