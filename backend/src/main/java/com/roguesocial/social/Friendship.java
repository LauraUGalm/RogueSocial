package com.roguesocial.social;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Two players who are friends, or one asking the other: a document in {@code friendships}.
 *
 * <p>Unlike a {@link Follow}, friendship has no direction, so the pair is stored in a fixed order:
 * the lower user ID in {@code lowId}, the higher in {@code highId}. A unique index on the two then
 * makes A–B and B–A the same document, so two players can't each send the other a request and end
 * up with two. {@code requesterId} remembers who asked.
 *
 * <p>An array {@code members: [a, b]} would read more naturally, but a unique index on an array
 * field applies to each element on its own: it would let a player be in only one friendship.
 */
@Document("friendships")
record Friendship(@Id String id, long lowId, long highId, long requesterId, Status status,
        Instant createdAt, Instant acceptedAt) {

    /** Stored as the name, {@code "PENDING"} or {@code "ACCEPTED"}. A declined request is removed. */
    enum Status {
        PENDING, ACCEPTED
    }

    /** The one who isn't {@code userId}. */
    long other(long userId) {
        return userId == lowId ? highId : lowId;
    }
}
