package com.roguesocial.social;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * One player liking one post: a document in {@code likes}. A unique index on the pair is what
 * stops anyone liking a post twice.
 */
@Document("likes")
record Like(@Id String id, @Field(targetType = FieldType.OBJECT_ID) String postId, long userId,
        Instant createdAt) {
}
