package com.roguesocial.user;

import java.util.NoSuchElementException;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.roguesocial.dungeon.Bank;

/** The bank balances on the {@code users} table. */
@Component
public class UserBank implements Bank {

    private final JdbcClient db;

    public UserBank(JdbcClient db) {
        this.db = db;
    }

    @Override
    public long depositGold(String username, int gold) {
        if (gold < 0) {
            throw new IllegalArgumentException("Can't deposit negative gold");
        }
        return db.sql("""
                UPDATE users SET bank_gold = bank_gold + :gold
                WHERE lower(username) = lower(:username)
                RETURNING bank_gold""")
                .param("gold", gold)
                .param("username", username)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new NoSuchElementException("No user " + username));
    }
}
