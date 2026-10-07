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
 * Builds a floor from a Wilson maze: entrance in the top-left cell, exit in the bottom-right,
 * gold piles scattered in between. Rooms and corridors come later (README section 6).
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
        return new Floor(walls, entrance, exit, gold);
    }

    private static Pos cellToBlock(int cx, int cy) {
        return new Pos(2 * cy + 1, 2 * cx + 1);
    }
}
