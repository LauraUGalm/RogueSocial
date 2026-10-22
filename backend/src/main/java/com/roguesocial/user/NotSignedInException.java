package com.roguesocial.user;

public class NotSignedInException extends RuntimeException {

    public NotSignedInException() {
        super("Sign in first");
    }
}
