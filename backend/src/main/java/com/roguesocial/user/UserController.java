package com.roguesocial.user;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Creating and looking up players. There is no sign-in yet, so anyone can create a user;
 * this is for development until accounts are designed.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    public record NewUser(String username, String email) {
    }

    /** What anyone may see about a player. Never includes the email. */
    public record PublicUser(long userId, String username, OffsetDateTime joined) {
        static PublicUser of(User u) {
            return new PublicUser(u.userId(), u.username(), u.createdAt());
        }
    }

    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PublicUser create(@RequestBody NewUser body) {
        return PublicUser.of(users.create(body.username(), body.email()));
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

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }
}
