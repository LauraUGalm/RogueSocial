package com.roguesocial.user;

import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Signing up and checking passwords. Passwords are stored as bcrypt hashes: slow on purpose, so
 * a stolen copy of the table can't be guessed through quickly, and salted, so two players with
 * the same password have different hashes.
 */
@Service
public class Accounts {

    /** About a quarter of a second per hash. Each step up doubles it. */
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(12);

    /**
     * Checked against when the username doesn't exist, so a wrong name takes as long as a wrong
     * password and the timing doesn't tell anyone which usernames are real.
     */
    private static final String NOBODY = BCRYPT.encode("no one has this password");

    private final UserRepository users;

    public Accounts(UserRepository users) {
        this.users = users;
    }

    /**
     * @throws IllegalArgumentException for a bad username or email
     * @throws WeakPasswordException if the password breaks {@link PasswordRules}
     * @throws UsernameTakenException if the username or email is in use
     */
    public User signUp(String username, String email, String password) {
        UserRepository.checkNew(username, email);
        List<String> problems = PasswordRules.problems(password, username);
        if (!problems.isEmpty()) {
            throw new WeakPasswordException(problems);
        }
        return users.create(username, email, BCRYPT.encode(password));
    }

    /** The player, if the password is theirs. A player with no password can't sign in. */
    public Optional<User> signIn(String username, String password) {
        Optional<User> user = username == null ? Optional.empty() : users.findByUsername(username);
        String hash = user.flatMap(u -> users.passwordHash(u.userId())).orElse(NOBODY);
        boolean matches = password != null && BCRYPT.matches(password, hash);
        return matches && hash != NOBODY ? user : Optional.empty();
    }
}
