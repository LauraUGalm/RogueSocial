package com.roguesocial.dungeon;

/** Where gold and exp go when a player leaves the dungeon alive. */
public interface Bank {

    /** The player's banked gold, total exp, and the level that exp has reached. */
    record Balance(long gold, long exp, int level) {
    }

    /**
     * Adds {@code gold} and {@code exp} to the player's bank, raising their level if the exp
     * reaches a new one.
     *
     * @return the player's balance afterwards
     */
    Balance deposit(String username, int gold, int exp);
}
