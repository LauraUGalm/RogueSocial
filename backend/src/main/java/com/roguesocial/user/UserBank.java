package com.roguesocial.user;

import java.util.NoSuchElementException;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.roguesocial.dungeon.Bank;
import com.roguesocial.social.NotEnoughGoldException;
import com.roguesocial.social.Purse;

/** The bank balances on the {@code users} table. */
@Component
public class UserBank implements Bank, Purse {

    private final JdbcClient db;

    public UserBank(JdbcClient db) {
        this.db = db;
    }

    /**
     * The level is worked out from the new exp total in Java ({@link Levels}), so it takes a
     * second statement; the transaction makes the two one change.
     */
    @Override
    @Transactional
    public Balance deposit(String username, int gold, int exp) {
        if (gold < 0 || exp < 0) {
            throw new IllegalArgumentException("Can't deposit negative gold or exp");
        }
        record Totals(long gold, long exp) {
        }
        Totals totals = db.sql("""
                UPDATE users SET bank_gold = bank_gold + :gold, exp = exp + :exp
                WHERE lower(username) = lower(:username)
                RETURNING bank_gold, exp""")
                .param("gold", gold)
                .param("exp", exp)
                .param("username", username)
                .query((rs, n) -> new Totals(rs.getLong("bank_gold"), rs.getLong("exp")))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("No user " + username));
        int level = db.sql("""
                UPDATE users SET level = GREATEST(level, :level)
                WHERE lower(username) = lower(:username)
                RETURNING level""")
                .param("level", Levels.levelFor(totals.exp()))
                .param("username", username)
                .query(Integer.class)
                .single();
        return new Balance(totals.gold(), totals.exp(), level);
    }

    /** @return the player's banked gold afterwards */
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

    /**
     * The {@code bank_gold >= :gold} condition makes the check and the deduction one statement,
     * so two posts sent at once can't both spend the same gold.
     */
    @Override
    public long spendGold(String username, int gold) {
        if (gold < 0) {
            throw new IllegalArgumentException("Can't spend negative gold");
        }
        return db.sql("""
                UPDATE users SET bank_gold = bank_gold - :gold
                WHERE lower(username) = lower(:username) AND bank_gold >= :gold
                RETURNING bank_gold""")
                .param("gold", gold)
                .param("username", username)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new NotEnoughGoldException(balance(username), gold));
    }

    @Override
    public void refundGold(String username, int gold) {
        depositGold(username, gold);
    }

    @Override
    public long balance(String username) {
        return db.sql("SELECT bank_gold FROM users WHERE lower(username) = lower(:username)")
                .param("username", username)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new NoSuchElementException("No user " + username));
    }
}
