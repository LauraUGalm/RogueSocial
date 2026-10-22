package com.roguesocial.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class LevelsTest {

    @Test
    void eachLevelTakesOnePercentMoreRoundedUp() {
        // Steps of 20, 21, 22, 23 more.
        assertThat(IntStream.rangeClosed(1, 5).mapToObj(Levels::expFor).toList())
                .isEqualTo(List.of(0L, 20L, 41L, 63L, 86L));
    }

    @Test
    void levelForIsTheHighestLevelReached() {
        assertThat(Levels.levelFor(0)).isEqualTo(1);
        assertThat(Levels.levelFor(19)).isEqualTo(1);
        assertThat(Levels.levelFor(20)).isEqualTo(2);
        assertThat(Levels.levelFor(40)).isEqualTo(2);
        assertThat(Levels.levelFor(41)).isEqualTo(3);
        assertThat(Levels.levelFor(63)).isEqualTo(4);
    }

    @Test
    void levelForAndExpForAgree() {
        for (int level = 1; level <= 200; level++) {
            assertThat(Levels.levelFor(Levels.expFor(level))).isEqualTo(level);
            assertThat(Levels.levelFor(Levels.expFor(level) - 1)).isEqualTo(Math.max(1, level - 1));
        }
    }
}
