package com.roguesocial.user;

import java.time.OffsetDateTime;

/**
 * A player. Mirrors a row of the {@code users} table (bank balances are left to the bank).
 *
 * @param power     how hard they hit
 * @param defense   how much damage they shrug off
 * @param maxHealth the most health they can have; each run starts with this much
 */
public record User(
        long userId,
        String username,
        String email,
        OffsetDateTime createdAt,
        int level,
        int power,
        int defense,
        int maxHealth) {
}
