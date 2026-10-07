package com.roguesocial.maze;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A maze stored as a block grid: every cell and every wall between cells is one block.
 * A maze of {@code cellCols x cellRows} cells is a {@code (2*cellRows+1) x (2*cellCols+1)} block grid.
 * Cell (cx, cy) lives at block (row = 2*cy+1, col = 2*cx+1).
 */
public final class Maze {

    private final int cellCols;
    private final int cellRows;
    private final boolean[][] walls;

    Maze(int cellCols, int cellRows) {
        this.cellCols = cellCols;
        this.cellRows = cellRows;
        this.walls = new boolean[2 * cellRows + 1][2 * cellCols + 1];
        for (boolean[] row : walls) {
            Arrays.fill(row, true);
        }
        for (int cy = 0; cy < cellRows; cy++) {
            for (int cx = 0; cx < cellCols; cx++) {
                walls[2 * cy + 1][2 * cx + 1] = false;
            }
        }
    }

    /** Removes the wall between two orthogonally adjacent cells. */
    void carve(int cx1, int cy1, int cx2, int cy2) {
        walls[cy1 + cy2 + 1][cx1 + cx2 + 1] = false;
    }

    public int cellCols() {
        return cellCols;
    }

    public int cellRows() {
        return cellRows;
    }

    public int rows() {
        return walls.length;
    }

    public int cols() {
        return walls[0].length;
    }

    public boolean isWall(int row, int col) {
        return walls[row][col];
    }

    /** One string per block row: '#' is a wall, '.' is open floor. */
    public List<String> toRows() {
        List<String> rows = new ArrayList<>(walls.length);
        for (boolean[] row : walls) {
            StringBuilder sb = new StringBuilder(row.length);
            for (boolean wall : row) {
                sb.append(wall ? '#' : '.');
            }
            rows.add(sb.toString());
        }
        return rows;
    }
}
