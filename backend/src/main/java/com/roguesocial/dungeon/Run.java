package com.roguesocial.dungeon;

import java.util.HashSet;
import java.util.Set;

/**
 * One player's trip through a floor. The server saves it after every turn, so a player can
 * close the tab at any moment and pick up exactly where they left off.
 */
public final class Run {

    public enum Status {
        ACTIVE,
        /** Left through the exit; the gold carried went to the bank. */
        LEFT
    }

    private final String id;
    private final String owner;
    private final Floor floor;
    private final Set<Pos> seen = new HashSet<>();
    private Pos player;
    private int turn;
    private int carriedGold;
    private Status status = Status.ACTIVE;

    public Run(String id, String owner, Floor floor) {
        this.id = id;
        this.owner = owner;
        this.floor = floor;
        this.player = floor.entrance();
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

    void see(Set<Pos> blocks) {
        seen.addAll(blocks);
    }
}
