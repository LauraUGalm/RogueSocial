package com.roguesocial.user;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.NoSuchElementException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.roguesocial.dungeon.PlayerStats;

/** Signing up, and looking up players. */
@RestController
@RequestMapping("/api/users")
public class UserController {

    public record NewUser(String username, String email, String password) {
    }

    /** What anyone may see about a player. Never includes the email. */
    public record PublicUser(
            long userId, String username, OffsetDateTime joined,
            int level, int power, int defense, int maxHealth) {
        static PublicUser of(User u) {
            PlayerStats.Stats stats = u.stats();
            return new PublicUser(u.userId(), u.username(), u.createdAt(),
                    stats.level(), stats.power(), stats.defense(), stats.maxHealth());
        }
    }

    private final UserRepository users;
    private final Accounts accounts;

    public UserController(UserRepository users, Accounts accounts) {
        this.users = users;
        this.accounts = accounts;
    }

    /** Signs up, and signs the new player in. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PublicUser create(@RequestBody NewUser body, HttpServletRequest request) {
        User user = accounts.signUp(body.username(), body.email(), body.password());
        Sessions.signIn(request, user);
        return PublicUser.of(user);
    }

    @GetMapping("/{username}")
    public PublicUser find(@PathVariable String username) {
        return users.findByUsername(username)
                .map(PublicUser::of)
                .orElseThrow(() -> new NoSuchElementException("No user " + username));
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound(NoSuchElementException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(UsernameTakenException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> taken(UsernameTakenException e) {
        return Map.of("error", e.getMessage());
    }

    /** {@code problems} lists what the password still needs, for the sign-up form to show. */
    @ExceptionHandler(WeakPasswordException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> weakPassword(WeakPasswordException e) {
        return Map.of("error", e.getMessage(), "problems", e.problems());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }
}
