package com.roguesocial.social;

public class NotYourPostException extends RuntimeException {

    public NotYourPostException() {
        super("Only the author can change or delete a post");
    }
}
