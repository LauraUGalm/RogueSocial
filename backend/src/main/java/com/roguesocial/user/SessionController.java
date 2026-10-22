package com.roguesocial.user;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.roguesocial.user.UserController.PublicUser;

/** Signing in and out. Signing up is {@code POST /api/users}. */
@RestController
@RequestMapping("/api/session")
public class SessionController {

    public record Credentials(String username, String password) {
    }

    /** One message for a wrong name and a wrong password, so neither says which usernames exist. */
    static class WrongPasswordException extends RuntimeException {
        WrongPasswordException() {
            super("Wrong username or password");
        }
    }

    private final Accounts accounts;
    private final UserRepository users;

    public SessionController(Accounts accounts, UserRepository users) {
        this.accounts = accounts;
        this.users = users;
    }

    @PostMapping
    public PublicUser signIn(@RequestBody Credentials body, HttpServletRequest request) {
        User user = accounts.signIn(body.username(), body.password()).orElseThrow(WrongPasswordException::new);
        Sessions.signIn(request, user);
        return PublicUser.of(user);
    }

    /** Who is signed in, or 401. */
    @GetMapping
    public PublicUser current(SignedIn me) {
        return users.findById(me.userId()).map(PublicUser::of).orElseThrow(NotSignedInException::new);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void signOut(HttpServletRequest request) {
        Sessions.signOut(request);
    }

    @ExceptionHandler(WrongPasswordException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> wrongPassword(WrongPasswordException e) {
        return Map.of("error", e.getMessage());
    }
}
