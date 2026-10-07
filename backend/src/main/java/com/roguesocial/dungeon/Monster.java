package com.roguesocial.dungeon;

/** One monster on a floor: what it is, where it is, and how much health it has left. */
public final class Monster {

    private final MonsterType type;
    private Pos pos;
    private int health;

    public Monster(MonsterType type, Pos pos) {
        this.type = type;
        this.pos = pos;
        this.health = type.maxHealth();
    }

    public MonsterType type() {
        return type;
    }

    public Pos pos() {
        return pos;
    }

    public int health() {
        return health;
    }

    public boolean isDead() {
        return health <= 0;
    }

    void moveTo(Pos p) {
        pos = p;
    }

    void hurt(int damage) {
        health = Math.max(0, health - damage);
    }
}
