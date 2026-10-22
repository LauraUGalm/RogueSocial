package com.roguesocial.user;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Who is signed in, kept in the servlet session. The browser holds only the session cookie
 * (JSESSIONID, which JavaScript can't read); the server keeps the rest in memory, so sessions,
 * like runs, are lost when it restarts.
 */
public final class Sessions {

    /** The session attribute holding the {@link SignedIn}. Public so tests can make a session. */
    public static final String KEY = "signedIn";

    private Sessions() {
    }

    /**
     * Starts a new session rather than reusing one the browser already had, so a session id
     * someone planted before sign-in is worthless afterwards.
     */
    static SignedIn signIn(HttpServletRequest request, User user) {
        signOut(request);
        SignedIn who = SignedIn.of(user);
        request.getSession(true).setAttribute(KEY, who);
        return who;
    }

    static void signOut(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    public static Optional<SignedIn> current(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? Optional.empty()
                : Optional.ofNullable((SignedIn) session.getAttribute(KEY));
    }
}
