package com.roguesocial.dungeon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;

import org.junit.jupiter.api.RepeatedTest;

import com.roguesocial.maze.WilsonMazeGenerator;

class FloorGeneratorTest {

    private final FloorGenerator generator = new FloorGenerator(new WilsonMazeGenerator());

    @RepeatedTest(10)
    void scattersGoldOnOpenFloorAwayFromEntranceAndExit() {
        Floor floor = generator.generate(new Random());

        int piles = 0;
        for (int r = 0; r < floor.rows(); r++) {
            for (int c = 0; c < floor.cols(); c++) {
                Pos p = new Pos(r, c);
                int gold = floor.goldAt(p);
                if (gold > 0) {
                    piles++;
                    assertThat(floor.isWall(p)).isFalse();
                    assertThat(gold).isBetween(FloorGenerator.GOLD_MIN, FloorGenerator.GOLD_MAX);
                }
            }
        }
        assertThat(piles).isEqualTo(FloorGenerator.GOLD_PILES);
        assertThat(floor.goldAt(floor.entrance())).isZero();
        assertThat(floor.goldAt(floor.exit())).isZero();
        assertThat(floor.isWall(floor.entrance())).isFalse();
        assertThat(floor.isWall(floor.exit())).isFalse();
    }
}
