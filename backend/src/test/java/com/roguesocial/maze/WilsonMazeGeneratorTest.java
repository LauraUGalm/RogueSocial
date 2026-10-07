package com.roguesocial.maze;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class WilsonMazeGeneratorTest {

    private final WilsonMazeGenerator generator = new WilsonMazeGenerator();

    @RepeatedTest(20)
    void producesAPerfectMaze() {
        Maze maze = generator.generate(15, 11, new Random());

        assertThat(maze.rows()).isEqualTo(23);
        assertThat(maze.cols()).isEqualTo(31);

        // A perfect maze is a spanning tree: every cell reachable and exactly cells-1 passages.
        int openPassages = 0;
        for (int r = 0; r < maze.rows(); r++) {
            for (int c = 0; c < maze.cols(); c++) {
                boolean isCell = r % 2 == 1 && c % 2 == 1;
                if (!isCell && !maze.isWall(r, c)) {
                    openPassages++;
                }
            }
        }
        assertThat(openPassages).isEqualTo(15 * 11 - 1);
        assertThat(reachableCells(maze)).isEqualTo(15 * 11);
    }

    @Test
    void outerBorderIsSolid() {
        Maze maze = generator.generate(8, 6, new Random(42));
        for (int c = 0; c < maze.cols(); c++) {
            assertThat(maze.isWall(0, c)).isTrue();
            assertThat(maze.isWall(maze.rows() - 1, c)).isTrue();
        }
        for (int r = 0; r < maze.rows(); r++) {
            assertThat(maze.isWall(r, 0)).isTrue();
            assertThat(maze.isWall(r, maze.cols() - 1)).isTrue();
        }
    }

    private static int reachableCells(Maze maze) {
        boolean[][] seen = new boolean[maze.rows()][maze.cols()];
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {1, 1});
        seen[1][1] = true;
        int cells = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            if (p[0] % 2 == 1 && p[1] % 2 == 1) {
                cells++;
            }
            for (int[] d : dirs) {
                int r = p[0] + d[0];
                int c = p[1] + d[1];
                if (!maze.isWall(r, c) && !seen[r][c]) {
                    seen[r][c] = true;
                    queue.add(new int[] {r, c});
                }
            }
        }
        return cells;
    }
}
