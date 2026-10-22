package com.roguesocial.user;

import java.time.OffsetDateTime;

import com.roguesocial.dungeon.PlayerStats;

/**
 * A player. Mirrors a row of the {@code users} table (bank balances are left to the bank).
 *
 * @param basePower     how hard they hit at level 1
 * @param baseDefense   how much damage they shrug off at level 1
 * @param baseMaxHealth the most health they can have at level 1
 */
public record User(
        long userId,
        String username,
        String email,
        OffsetDateTime createdAt,
        int level,
        int basePower,
        int baseDefense,
        int baseMaxHealth) {

    /** Their stats at their level: the base stats plus what each level after the first adds. */
    public PlayerStats.Stats stats() {
        int levelsGained = level - 1;
        return new PlayerStats.Stats(level,
                basePower + levelsGained * Levels.POWER_PER_LEVEL,
                baseDefense + levelsGained * Levels.DEFENSE_PER_LEVEL,
                baseMaxHealth + levelsGained * Levels.HEALTH_PER_LEVEL);
    }
}
