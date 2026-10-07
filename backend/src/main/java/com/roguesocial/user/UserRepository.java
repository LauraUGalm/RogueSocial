package com.roguesocial.user;

import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads and writes the {@code users} table (db/schema.sql). */
@Repository
public class UserRepository {

    /** Same rule as the users_username_format check, so bad names fail before reaching the DB. */
    static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,32}");

    private static final RowMapper<User> ROW = (rs, n) -> new User(
            rs.getLong("user_id"),
            rs.getString("username"),
            rs.getString("email"),
            rs.getObject("created_at", java.time.OffsetDateTime.class),
            rs.getInt("level"),
            rs.getInt("power"),
            rs.getInt("defense"),
            rs.getInt("max_health"));

    private static final String COLUMNS = "user_id, username, email, created_at, level, power, defense, max_health";

    private final JdbcClient db;

    public UserRepository(JdbcClient db) {
        this.db = db;
    }

    public User create(String username, String email) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException(
                    "A username is 3 to 32 letters, digits or underscores");
        }
        if (email == null || !email.contains("@") || email.length() > 254) {
            throw new IllegalArgumentException("That doesn't look like an email address");
        }
        try {
            return db.sql("INSERT INTO users (username, email) VALUES (:username, :email) RETURNING "
                    + COLUMNS)
                    .param("username", username)
                    .param("email", email.trim())
                    .query(ROW)
                    .single();
        } catch (DuplicateKeyException e) {
            throw new UsernameTakenException("That username or email is already taken");
        }
    }

    public Optional<User> findById(long userId) {
        return db.sql("SELECT " + COLUMNS + " FROM users WHERE user_id = :id")
                .param("id", userId)
                .query(ROW)
                .optional();
    }

    /** Ignores case, matching the unique index. */
    public Optional<User> findByUsername(String username) {
        return db.sql("SELECT " + COLUMNS + " FROM users WHERE lower(username) = lower(:username)")
                .param("username", username)
                .query(ROW)
                .optional();
    }
}
