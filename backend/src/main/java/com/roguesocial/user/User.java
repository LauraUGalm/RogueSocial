package com.roguesocial.user;

import java.time.OffsetDateTime;

/** A player. Mirrors a row of the {@code users} table. */
public record User(long userId, String username, String email, OffsetDateTime createdAt) {
}
