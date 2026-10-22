package com.roguesocial.user;

import java.util.List;

/** The password breaks {@link PasswordRules}. {@link #problems()} lists what it still needs. */
public class WeakPasswordException extends RuntimeException {

    private final List<String> problems;

    public WeakPasswordException(List<String> problems) {
        super("That password isn't strong enough: " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
