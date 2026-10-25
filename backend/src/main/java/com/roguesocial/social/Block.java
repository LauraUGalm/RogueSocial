package com.roguesocial.social;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One player blocking another: a document in {@code blocks}.
 *
 * <p>Unlike a {@link Friendship}, a block has a direction: only the blocker can undo it, and only
 * the blocker sees it in their list. So it is stored like a {@link Follow}, blocker then blocked,
 * and checks that care about either direction ask for both.
 */
@Document("blocks")
record Block(@Id String id, long blockerId, long blockedId, Instant createdAt) {
}
