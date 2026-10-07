package com.roguesocial.maze;

import java.util.Random;

import org.springframework.stereotype.Component;

/**
 * Generates perfect mazes (exactly one path between any two cells) with Wilson's algorithm:
 * loop-erased random walks from unvisited cells until they hit the maze, which yields a
 * uniformly random spanning tree with no directional bias.
 */
@Component
public class WilsonMazeGenerator {

    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DY = {0, 0, 1, -1};

    public Maze generate(int cellCols, int cellRows, Random random) {
        if (cellCols < 1 || cellRows < 1) {
            throw new IllegalArgumentException("Maze must be at least 1x1 cells");
        }
        Maze maze = new Maze(cellCols, cellRows);
        int cellCount = cellCols * cellRows;
        boolean[] inMaze = new boolean[cellCount];
        // For each cell on the current walk, the cell the walk last moved to from it.
        // Overwriting on revisit is what erases loops.
        int[] next = new int[cellCount];

        inMaze[random.nextInt(cellCount)] = true;
        int remaining = cellCount - 1;

        for (int start = 0; remaining > 0; start++) {
            if (inMaze[start]) {
                continue;
            }
            // Random walk from start until it reaches the maze.
            int cell = start;
            while (!inMaze[cell]) {
                int neighbor = randomNeighbor(cell, cellCols, cellRows, random);
                next[cell] = neighbor;
                cell = neighbor;
            }
            // Retrace the loop-erased path, carving it into the maze.
            cell = start;
            while (!inMaze[cell]) {
                int to = next[cell];
                maze.carve(cell % cellCols, cell / cellCols, to % cellCols, to / cellCols);
                inMaze[cell] = true;
                remaining--;
                cell = to;
            }
        }
        return maze;
    }

    private static int randomNeighbor(int cell, int cols, int rows, Random random) {
        int x = cell % cols;
        int y = cell / cols;
        while (true) {
            int d = random.nextInt(4);
            int nx = x + DX[d];
            int ny = y + DY[d];
            if (nx >= 0 && nx < cols && ny >= 0 && ny < rows) {
                return ny * cols + nx;
            }
        }
    }
}
