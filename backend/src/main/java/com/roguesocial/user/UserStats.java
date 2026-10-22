package com.roguesocial.user;

import java.util.NoSuchElementException;

import org.springframework.stereotype.Component;

import com.roguesocial.dungeon.PlayerStats;

/** Player stats from the {@code users} table: their base stats raised by their level. */
@Component
public class UserStats implements PlayerStats {

    private final UserRepository users;

    public UserStats(UserRepository users) {
        this.users = users;
    }

    @Override
    public Stats lookup(String username) {
        User u = users.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("No user " + username));
        return u.stats();
    }
}
