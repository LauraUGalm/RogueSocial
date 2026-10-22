package com.roguesocial.dungeon;

import java.util.Map;

/**
 * A kind of monster: a row of the {@code enemies} table. Saved floors store the name, never a
 * picture: the browser looks up the sprite by {@link #name()}.
 *
 * @param power how hard it hits
 * @param exp what the player earns for killing one
 */
public record EnemyType(String name, int maxHealth, int power, int defense, int exp) {

    /** How some attacks read in a message. Anything not listed "hits". */
    private static final Map<String, String> VERBS = Map.of("bat", "bites", "scorpion", "stings");

    /** "The bat bites you for 1." */
    public String verb() {
        return VERBS.getOrDefault(name, "hits");
    }
}
