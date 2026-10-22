package com.roguesocial.enemy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.roguesocial.dungeon.EnemyType;

/** Runs against the real database (read only); skipped unless ROGUE_DB_PASSWORD is set. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ROGUE_DB_PASSWORD", matches = ".+")
class EnemyRepositoryTest {

    @Autowired
    private EnemyRepository enemies;

    @Test
    void readsTheSeededEnemies() {
        assertThat(enemies.byName("bat")).isEqualTo(new EnemyType("bat", 3, 2, 1, 1));
        assertThat(enemies.byName("scorpion")).isEqualTo(new EnemyType("scorpion", 10, 7, 3, 3));
        assertThat(enemies.all()).extracting(EnemyType::name).contains("bat", "scorpion");
    }

    @Test
    void anUnknownEnemyIsAnError() {
        assertThatThrownBy(() -> enemies.byName("unicorn")).isInstanceOf(NoSuchElementException.class);
    }
}
