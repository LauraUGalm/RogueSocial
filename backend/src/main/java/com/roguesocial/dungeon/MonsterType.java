package com.roguesocial.dungeon;

/**
 * The kinds of monster, weakest first. Saved floors store the kind, never a picture: the browser
 * looks up its sprite by {@link #sprite()}.
 */
public enum MonsterType {
    BAT("bat", "bites", 3, 2, 1),
    SCORPION("scorpion", "stings", 10, 7, 3);

    private final String sprite;
    private final String verb;
    private final int maxHealth;
    private final int strength;
    private final int defense;

    MonsterType(String sprite, String verb, int maxHealth, int strength, int defense) {
        this.sprite = sprite;
        this.verb = verb;
        this.maxHealth = maxHealth;
        this.strength = strength;
        this.defense = defense;
    }

    /** The sprite's name in sprites.json, also used in messages ("the bat"). */
    public String sprite() {
        return sprite;
    }

    /** How its attack reads in a message: "The bat bites you for 1." */
    public String verb() {
        return verb;
    }

    public int maxHealth() {
        return maxHealth;
    }

    public int strength() {
        return strength;
    }

    public int defense() {
        return defense;
    }
}
