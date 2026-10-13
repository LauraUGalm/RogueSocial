package com.roguesocial.dungeon;

import java.util.Map;

/**
 * A kind of monster: a row of the {@code enemies} table. Saved floors store the name, never a
 * picture: the browser looks up the sprite by {@link #name()}.
 *
 * @param power how hard it hits
 */
public record EnemyType(String name, int maxHealth, int power, int defense) {

    /** How some attacks read in a message. Anything not listed "hits". */
    private static final Map<String, String> VERBS = Map.of("bat", "bites", "scorpion", "stings");

    /** "The bat bites you for 1." */
    public String verb() {
        return VERBS.getOrDefault(name, "hits");
    }
}
