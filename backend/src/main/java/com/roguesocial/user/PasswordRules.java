package com.roguesocial.user;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a new password must be, following NIST SP 800-63B: long, and not easy to guess. There are
 * no rules about mixing uppercase, numbers and symbols. They push people toward "Password1!",
 * and length is what makes a password hard to guess.
 */
final class PasswordRules {

    /** NIST's minimum when the password is the only thing protecting the account. */
    static final int MIN_LENGTH = 15;
    /** bcrypt only reads the first 72 bytes, so anything past that would be ignored. */
    static final int MAX_BYTES = 72;

    /** Keyboard rows and the alphabet. A password that is a stretch of one of these is a walk. */
    private static final List<String> RUNS = List.of(
            "abcdefghijklmnopqrstuvwxyz",
            "qwertyuiopasdfghjklzxcvbnm",
            "qazwsxedcrfvtgbyhnujmikolp",
            "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik9ol0p");

    /** Words people reach for first. Lower case, one per line. */
    private static final Set<String> COMMON = load("common-passwords.txt");

    private PasswordRules() {
    }

    /** Everything wrong with this password, in plain words. Empty when it's fine. */
    static List<String> problems(String password, String username) {
        if (password == null || password.isEmpty()) {
            return List.of("A password is needed");
        }
        List<String> problems = new ArrayList<>();
        if (password.codePointCount(0, password.length()) < MIN_LENGTH) {
            problems.add("at least " + MIN_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            problems.add("at most " + MAX_BYTES + " characters (fewer with emoji or accented letters)");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (username != null && username.length() >= 3 && lower.contains(username.toLowerCase(Locale.ROOT))) {
            problems.add("not your username inside it");
        }
        if (guessable(lower) || guessable(core(lower))) {
            problems.add("not a common password or an easy pattern, like passwordpassword or qwertyuiop");
        }
        return problems;
    }

    /**
     * The password without the digits and symbols people tack on either end, so "Password1234567"
     * is judged as "password".
     */
    private static String core(String lower) {
        return lower.replaceAll("^[^\\p{L}]+|[^\\p{L}]+$", "");
    }

    private static boolean guessable(String s) {
        if (s.isEmpty()) {
            return false;
        }
        return COMMON.contains(s)
                || s.codePoints().distinct().count() < 5
                || repeats(s)
                || RUNS.stream().anyMatch(run -> (run + run).contains(s)
                        || new StringBuilder(run + run).reverse().toString().contains(s));
    }

    /**
     * Whether the password is mostly one chunk said again, like "passwordpassword" or
     * "123456789012345": each character matches the one {@code p} places back, for a {@code p}
     * no more than two thirds of the length.
     */
    private static boolean repeats(String s) {
        int n = s.length();
        for (int p = 1; p * 3 <= n * 2; p++) {
            boolean same = true;
            for (int i = p; i < n && same; i++) {
                same = s.charAt(i) == s.charAt(i - p);
            }
            if (same) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> load(String resource) {
        try (InputStream in = PasswordRules.class.getResourceAsStream(resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
