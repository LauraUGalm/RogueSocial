package com.roguesocial.dungeon;

/** Where a run gets the player's stats when it starts. */
public interface PlayerStats {

    /**
     * @param power     how hard the player hits
     * @param maxHealth the health a run starts with
     */
    record Stats(int level, int power, int defense, int maxHealth) {
    }

    Stats lookup(String username);
}
