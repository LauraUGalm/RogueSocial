package com.roguesocial.dungeon;

/** Where gold goes when a player leaves the dungeon alive. */
public interface Bank {

    /**
     * Adds {@code gold} to the player's bank.
     *
     * @return the player's banked gold afterwards
     */
    long depositGold(String username, int gold);
}
