package com.roguesocial.dungeon;

import java.util.HashMap;
import java.util.Map;

/**
 * One dungeon floor, kept whole on the server. The player only ever learns the parts they see.
 * Gold piles are removed as the player picks them up.
 */
public final class Floor {

    private final boolean[][] walls;
    private final Pos entrance;
    private final Pos exit;
    private final Map<Pos, Integer> gold;

    public Floor(boolean[][] walls, Pos entrance, Pos exit, Map<Pos, Integer> gold) {
        this.walls = walls;
        this.entrance = entrance;
        this.exit = exit;
        this.gold = new HashMap<>(gold);
    }

    public int rows() {
        return walls.length;
    }

    public int cols() {
        return walls[0].length;
    }

    /** Anything outside the grid counts as wall. */
    public boolean isWall(Pos p) {
        return p.row() < 0 || p.row() >= rows() || p.col() < 0 || p.col() >= cols()
                || walls[p.row()][p.col()];
    }

    public Pos entrance() {
        return entrance;
    }

    public Pos exit() {
        return exit;
    }

    /** The gold lying at {@code p}, or 0. */
    public int goldAt(Pos p) {
        return gold.getOrDefault(p, 0);
    }

    /** Removes and returns the gold lying at {@code p}, or 0 if there is none. */
    public int takeGold(Pos p) {
        Integer amount = gold.remove(p);
        return amount == null ? 0 : amount;
    }
}
