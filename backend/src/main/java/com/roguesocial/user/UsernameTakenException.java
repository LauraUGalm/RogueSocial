package com.roguesocial.user;

/** The username or email is already in use, ignoring case. */
public class UsernameTakenException extends RuntimeException {

    public UsernameTakenException(String message) {
        super(message);
    }
}
