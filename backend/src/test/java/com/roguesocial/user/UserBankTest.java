package com.roguesocial.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Runs against the real database; see {@link UserRepositoryTest}. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ROGUE_DB_PASSWORD", matches = ".+")
class UserBankTest {

    @Autowired
    private UserBank bank;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcClient db;

    @AfterEach
    void cleanUp() {
        db.sql("DELETE FROM users WHERE username LIKE 'zztest\\_%'").update();
    }

    @Test
    void depositsAddUp() {
        String name = "zztest_" + UUID.randomUUID().toString().substring(0, 8);
        users.create(name, name + "@example.com");

        assertThat(bank.depositGold(name, 12)).isEqualTo(12);
        assertThat(bank.depositGold(name.toUpperCase().replace("ZZTEST_", "zztest_"), 30))
                .isEqualTo(42);
        assertThat(bank.depositGold(name, 0)).isEqualTo(42);
    }

    @Test
    void anUnknownUserIsAnError() {
        assertThatThrownBy(() -> bank.depositGold("zztest_nobody", 5))
                .isInstanceOf(NoSuchElementException.class);
    }
}
