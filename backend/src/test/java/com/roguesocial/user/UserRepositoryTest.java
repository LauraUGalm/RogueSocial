package com.roguesocial.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Runs against the real configured database, so it is skipped unless ROGUE_DB_PASSWORD is set.
 * Every user it makes is named zztest_..., and they are all deleted afterwards.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ROGUE_DB_PASSWORD", matches = ".+")
class UserRepositoryTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcClient db;

    @AfterEach
    void cleanUp() {
        db.sql("DELETE FROM users WHERE username LIKE 'zztest\\_%'").update();
    }

    private static String testName() {
        return "zztest_" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void createsAndFindsAUser() {
        String name = testName();
        User created = users.create(name, name + "@example.com");

        assertThat(created.userId()).isPositive();
        assertThat(created.createdAt()).isNotNull();
        assertThat(users.findById(created.userId())).contains(created);
        assertThat(users.findByUsername(name.toUpperCase())).contains(created);
    }

    @Test
    void usernamesAreUniqueIgnoringCase() {
        String name = testName();
        users.create(name, name + "@example.com");

        assertThatThrownBy(() -> users.create(name.toUpperCase().replace("ZZTEST_", "zztest_"),
                "other-" + name + "@example.com"))
                .isInstanceOf(UsernameTakenException.class);
    }

    @Test
    void emailsAreUniqueIgnoringCase() {
        String name = testName();
        users.create(name, name + "@example.com");

        assertThatThrownBy(() -> users.create(testName(), name.toUpperCase() + "@EXAMPLE.COM"))
                .isInstanceOf(UsernameTakenException.class);
    }

    @Test
    void rejectsABadUsernameBeforeTheDatabase() {
        assertThatThrownBy(() -> users.create("no spaces allowed", "x@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> users.create("ab", "x@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownUsersAreEmpty() {
        assertThat(users.findByUsername(testName())).isEmpty();
        assertThat(users.findById(-1)).isEmpty();
    }
}
