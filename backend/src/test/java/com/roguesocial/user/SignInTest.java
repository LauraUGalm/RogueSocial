package com.roguesocial.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Signing up, in and out through the API. Runs against the real database, so it is skipped
 * unless ROGUE_DB_PASSWORD is set; every user it makes is a zztest_... and is deleted afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "ROGUE_DB_PASSWORD", matches = ".+")
class SignInTest {

    private static final String PASSWORD = "troll bait on floor three";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient db;

    private final String name = "zztest_" + UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void cleanUp() {
        db.sql("DELETE FROM users WHERE username LIKE 'zztest\\_%'").update();
    }

    private ResultActions signUp(String password) throws Exception {
        return mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(
                "{\"username\": \"%s\", \"email\": \"%s@example.com\", \"password\": \"%s\"}"
                        .formatted(name, name, password)));
    }

    private ResultActions signIn(String password) throws Exception {
        return mvc.perform(post("/api/session").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(name, password)));
    }

    @Test
    void signingUpSignsYouIn() throws Exception {
        MockHttpSession session = (MockHttpSession) signUp(PASSWORD)
                .andExpect(status().isCreated())
                .andReturn().getRequest().getSession(false);

        mvc.perform(get("/api/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(name));
        String hash = db.sql("SELECT password_hash FROM users WHERE username = :n").param("n", name)
                .query(String.class).single();
        assertThat(hash).startsWith("$2a$12$").doesNotContain(PASSWORD);
    }

    @Test
    void aWeakPasswordListsWhatItNeeds() throws Exception {
        signUp("password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.problems[0]").value("at least 15 characters"))
                .andExpect(jsonPath("$.problems.length()").value(2));
        assertThat(db.sql("SELECT count(*) FROM users WHERE username = :n").param("n", name)
                .query(Long.class).single()).isZero();
    }

    @Test
    void signInAndOut() throws Exception {
        signUp(PASSWORD);

        MockHttpSession session = (MockHttpSession) signIn(PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(name))
                .andReturn().getRequest().getSession(false);
        mvc.perform(get("/api/me").session(session)).andExpect(jsonPath("$.username").value(name));

        mvc.perform(delete("/api/session").session(session)).andExpect(status().isNoContent());
        mvc.perform(get("/api/session").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void aWrongPasswordAndAWrongNameLookTheSame() throws Exception {
        signUp(PASSWORD);

        String wrongPassword = signIn("Not-It-123").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String wrongName = mvc.perform(post("/api/session").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"zztest_nobody\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(wrongName).isEqualTo(wrongPassword);
    }

    @Test
    void aPlayerWithNoPasswordCantSignIn() throws Exception {
        db.sql("INSERT INTO users (username, email) VALUES (:n, :n || '@example.com')").param("n", name).update();

        signIn("").andExpect(status().isUnauthorized());
        signIn(PASSWORD).andExpect(status().isUnauthorized());
    }
}
