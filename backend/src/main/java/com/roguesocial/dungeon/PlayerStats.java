package com.roguesocial.dungeon;

/** Where a run gets the player's stats when it starts. */
public interface PlayerStats {

    /**
     * @param power     the player's strength in an attack
     * @param maxHealth the health a run starts with
     */
    record Stats(int level, int power, int defense, int maxHealth) {
    }

    Stats lookup(String username);
}
