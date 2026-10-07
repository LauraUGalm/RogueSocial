package com.roguesocial.dungeon;

import java.util.List;
import java.util.Map;

/** Builds small hand-drawn floors for tests. */
final class TestFloors {

    private TestFloors() {
    }

    /** '#' wall, anything else floor. */
    static Floor floor(List<String> rows, Pos entrance, Pos exit, Map<Pos, Integer> gold) {
        boolean[][] walls = new boolean[rows.size()][rows.get(0).length()];
        for (int r = 0; r < rows.size(); r++) {
            for (int c = 0; c < rows.get(r).length(); c++) {
                walls[r][c] = rows.get(r).charAt(c) == '#';
            }
        }
        return new Floor(walls, entrance, exit, gold);
    }
}
