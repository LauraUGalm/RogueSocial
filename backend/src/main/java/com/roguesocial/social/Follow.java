package com.roguesocial.social;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One player following another: a document in {@code follows}. The same shape as a join table
 * in Postgres. Indexes on both ids answer "who do I follow" and "who follows me".
 */
@Document("follows")
record Follow(@Id String id, long followerId, long followeeId, Instant createdAt) {
}
