package com.roguesocial.dungeon;

import java.util.List;
import java.util.Map;
import java.util.Random;

/** Builds small hand-drawn floors for tests. */
final class TestFloors {

    private TestFloors() {
    }

    /** The starting player in these tests: level 1, 5 power, 5 defense, 20 health. */
    static final PlayerStats.Stats STATS = new PlayerStats.Stats(1, 5, 5, 20);

    static Floor floor(List<String> rows, Pos entrance, Pos exit, Map<Pos, Integer> gold) {
        return floor(rows, entrance, exit, gold, List.of());
    }

    /** '#' wall, anything else floor. */
    static Floor floor(List<String> rows, Pos entrance, Pos exit, Map<Pos, Integer> gold,
            List<Monster> monsters) {
        boolean[][] walls = new boolean[rows.size()][rows.get(0).length()];
        for (int r = 0; r < rows.size(); r++) {
            for (int c = 0; c < rows.get(r).length(); c++) {
                walls[r][c] = rows.get(r).charAt(c) == '#';
            }
        }
        return new Floor(walls, entrance, exit, gold, monsters);
    }

    /** A generator that always returns the floor {@code make} builds. */
    static FloorGenerator always(java.util.function.Supplier<Floor> make) {
        return new FloorGenerator(null) {
            @Override
            public Floor generate(Random random) {
                return make.get();
            }
        };
    }

    /**
     * A Random whose coin flips are scripted: {@code true} means the monsters go first. Once the
     * script runs out, the player always goes first. Wandering monsters take their first option.
     */
    static Random coins(Boolean... monstersFirst) {
        java.util.Deque<Boolean> script = new java.util.ArrayDeque<>(List.of(monstersFirst));
        return new Random() {
            @Override
            public boolean nextBoolean() {
                return script.isEmpty() ? false : script.poll();
            }

            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
    }
}
