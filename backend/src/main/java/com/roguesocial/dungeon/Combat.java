package com.roguesocial.dungeon;

/** The combat rules. */
public final class Combat {

    private Combat() {
    }

    /** An attack does the attacker's power minus the defender's defense, but always at least 1. */
    public static int damage(int power, int defense) {
        return Math.max(1, power - defense);
    }
}
