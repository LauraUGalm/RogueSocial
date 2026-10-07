package com.roguesocial.dungeon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One dungeon floor, kept whole on the server. The player only ever learns the parts they see.
 * Gold piles are removed as the player picks them up, and monsters as they are killed.
 */
public final class Floor {

    private final boolean[][] walls;
    private final Pos entrance;
    private final Pos exit;
    private final Map<Pos, Integer> gold;
    private final List<Monster> monsters;

    public Floor(boolean[][] walls, Pos entrance, Pos exit, Map<Pos, Integer> gold,
            List<Monster> monsters) {
        this.walls = walls;
        this.entrance = entrance;
        this.exit = exit;
        this.gold = new HashMap<>(gold);
        this.monsters = new ArrayList<>(monsters);
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

    /** The living monsters, in the order they take their turns. */
    public List<Monster> monsters() {
        return monsters;
    }

    public Optional<Monster> monsterAt(Pos p) {
        return monsters.stream().filter(m -> m.pos().equals(p)).findFirst();
    }

    void remove(Monster monster) {
        monsters.remove(monster);
    }
}
