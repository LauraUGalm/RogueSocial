package com.roguesocial.dungeon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class LineOfSightTest {

    private final Floor floor = TestFloors.floor(List.of(
            "###########",
            "#.........#",
            "#####.#####",
            "#.........#",
            "###########"),
            new Pos(1, 1), new Pos(3, 9), Map.of());

    @Test
    void seesAlongAnOpenCorridor() {
        Set<Pos> seen = LineOfSight.visible(floor, new Pos(1, 1));
        assertThat(seen).contains(new Pos(1, 2), new Pos(1, 6));
    }

    @Test
    void stopsAtTheRadius() {
        Set<Pos> seen = LineOfSight.visible(floor, new Pos(1, 1));
        assertThat(seen).doesNotContain(new Pos(1, 1 + LineOfSight.RADIUS + 1));
    }

    @Test
    void seesWallsButNotPastThem() {
        Set<Pos> seen = LineOfSight.visible(floor, new Pos(1, 1));
        assertThat(seen).contains(new Pos(2, 1), new Pos(0, 1));
        assertThat(seen).doesNotContain(new Pos(3, 1), new Pos(3, 2));
    }

    @Test
    void seesThroughAGap() {
        Set<Pos> seen = LineOfSight.visible(floor, new Pos(1, 5));
        assertThat(seen).contains(new Pos(2, 5), new Pos(3, 5));
        assertThat(seen).doesNotContain(new Pos(3, 1));
    }
}
