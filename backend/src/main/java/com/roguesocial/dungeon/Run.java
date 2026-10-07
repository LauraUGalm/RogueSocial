package com.roguesocial.dungeon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One player's trip through a floor. The server saves it after every turn, so a player can
 * close the tab at any moment and pick up exactly where they left off.
 */
public final class Run {

    public enum Status {
        ACTIVE,
        /** Left through the exit; the gold carried went to the bank. */
        LEFT,
        /** Killed. The gold carried is lost. */
        DIED
    }

    private final String id;
    private final String owner;
    private final Floor floor;
    private final PlayerStats.Stats stats;
    private final Set<Pos> seen = new HashSet<>();
    private final List<TurnRecord> history = new ArrayList<>();
    private Pos player;
    private int turn;
    private int carriedGold;
    private int health;
    private Status status = Status.ACTIVE;

    /** {@code stats} are the player's as the run starts; the run begins at full health. */
    public Run(String id, String owner, PlayerStats.Stats stats, Floor floor) {
        this.id = id;
        this.owner = owner;
        this.stats = stats;
        this.floor = floor;
        this.player = floor.entrance();
        this.health = stats.maxHealth();
    }

    public String id() {
        return id;
    }

    /** The username of the player whose run this is. */
    public String owner() {
        return owner;
    }

    public Floor floor() {
        return floor;
    }

    public PlayerStats.Stats stats() {
        return stats;
    }

    public int health() {
        return health;
    }

    public Pos player() {
        return player;
    }

    /** Counts the player's completed turns. Each request must name the turn it is for. */
    public int turn() {
        return turn;
    }

    /** Gold picked up this run. It is still at risk until banked. */
    public int carriedGold() {
        return carriedGold;
    }

    public Status status() {
        return status;
    }

    /** Every block the player has ever seen on this floor: their map. */
    public Set<Pos> seen() {
        return seen;
    }

    /** Every request made to this run, oldest first. For debugging. */
    public List<TurnRecord> history() {
        return history;
    }

    void record(TurnRecord entry) {
        history.add(entry);
    }

    void moveTo(Pos p) {
        player = p;
    }

    void addGold(int amount) {
        carriedGold += amount;
    }

    void endTurn() {
        turn++;
    }

    void leave() {
        status = Status.LEFT;
    }

    void hurt(int damage) {
        health = Math.max(0, health - damage);
    }

    void die() {
        status = Status.DIED;
    }

    void see(Set<Pos> blocks) {
        seen.addAll(blocks);
    }
}
