package com.roguesocial.dungeon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.stereotype.Component;

import com.roguesocial.maze.Maze;
import com.roguesocial.maze.WilsonMazeGenerator;

/**
 * Builds a floor from a Wilson maze with a few rooms cleared out of it: entrance in the top-left
 * cell, exit in the bottom-right, gold piles and monsters scattered in between.
 */
@Component
public class FloorGenerator {

    /** Level 1 floor size: the same as Letter Maze. */
    static final int CELL_COLS = 15;
    static final int CELL_ROWS = 11;

    // Placeholder numbers until the economy is designed (README section 2).
    static final int GOLD_PILES = 8;
    static final int GOLD_MIN = 5;
    static final int GOLD_MAX = 25;

    // Placeholder numbers until levels are designed (README section 6).
    static final int BATS = 4;
    static final int SCORPIONS = 2;
    /** Monsters start at least this many steps (ignoring walls) from the entrance. */
    static final int MONSTER_MIN_DISTANCE = 12;

    private final WilsonMazeGenerator mazes;

    public FloorGenerator(WilsonMazeGenerator mazes) {
        this.mazes = mazes;
    }

    public Floor generate(Random random) {
        Maze maze = mazes.generate(CELL_COLS, CELL_ROWS, random);
        boolean[][] walls = new boolean[maze.rows()][maze.cols()];
        for (int r = 0; r < maze.rows(); r++) {
            for (int c = 0; c < maze.cols(); c++) {
                walls[r][c] = maze.isWall(r, c);
            }
        }
        RoomCarver.carve(walls, CELL_COLS, CELL_ROWS, random);

        Pos entrance = cellToBlock(0, 0);
        Pos exit = cellToBlock(CELL_COLS - 1, CELL_ROWS - 1);

        List<Pos> spots = new ArrayList<>();
        for (int cy = 0; cy < CELL_ROWS; cy++) {
            for (int cx = 0; cx < CELL_COLS; cx++) {
                Pos p = cellToBlock(cx, cy);
                if (!p.equals(entrance) && !p.equals(exit)) {
                    spots.add(p);
                }
            }
        }
        Collections.shuffle(spots, random);

        Map<Pos, Integer> gold = new HashMap<>();
        for (Pos p : spots.subList(0, GOLD_PILES)) {
            gold.put(p, GOLD_MIN + random.nextInt(GOLD_MAX - GOLD_MIN + 1));
        }

        List<Monster> monsters = new ArrayList<>();
        List<Pos> lairs = spots.subList(GOLD_PILES, spots.size()).stream()
                .filter(p -> Math.abs(p.row() - entrance.row()) + Math.abs(p.col() - entrance.col())
                        >= MONSTER_MIN_DISTANCE)
                .toList();
        for (int i = 0; i < BATS + SCORPIONS && i < lairs.size(); i++) {
            monsters.add(new Monster(i < BATS ? MonsterType.BAT : MonsterType.SCORPION, lairs.get(i)));
        }
        return new Floor(walls, entrance, exit, gold, monsters);
    }

    private static Pos cellToBlock(int cx, int cy) {
        return new Pos(2 * cy + 1, 2 * cx + 1);
    }
}
