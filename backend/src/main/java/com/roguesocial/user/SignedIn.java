package com.roguesocial.user;

import java.io.Serializable;

/**
 * Who a request comes from. Kept in the session when they sign in, and handed to any controller
 * method that takes one as a parameter (see {@code web.SignedInResolver}).
 */
public record SignedIn(long userId, String username) implements Serializable {

    static SignedIn of(User user) {
        return new SignedIn(user.userId(), user.username());
    }
}
