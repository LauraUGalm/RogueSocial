package com.roguesocial.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.roguesocial.dungeon.PlayerStats;

class UserTest {

    private static User atLevel(int level) {
        return new User(1, "laura", "laura@localhost", OffsetDateTime.now(), level, 5, 5, 20);
    }

    @Test
    void levelOneIsTheBaseStats() {
        assertThat(atLevel(1).stats()).isEqualTo(new PlayerStats.Stats(1, 5, 5, 20));
    }

    @Test
    void eachLevelAddsOnePowerOneDefenseAndTwoHealth() {
        assertThat(atLevel(2).stats()).isEqualTo(new PlayerStats.Stats(2, 6, 6, 22));
        assertThat(atLevel(5).stats()).isEqualTo(new PlayerStats.Stats(5, 9, 9, 28));
    }
}
