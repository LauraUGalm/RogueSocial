package com.roguesocial.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class RunControllerTest {

    @Autowired
    private MockMvc mvc;

    private String startRun() throws Exception {
        String body = mvc.perform(post("/api/runs"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.player.row").value(0))
                .andExpect(jsonPath("$.player.col").value(0))
                .andExpect(jsonPath("$.turn").value(0))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.runId");
    }

    @Test
    void startAndResumeARun() throws Exception {
        String id = startRun();
        mvc.perform(get("/api/runs/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remembered").isArray());
    }

    @Test
    void aTurnForTheWrongNumberIsAConflict() throws Exception {
        String id = startRun();
        mvc.perform(post("/api/runs/" + id + "/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"turn\": 7, \"action\": \"NORTH\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentTurn").value(0));
    }

    @Test
    void aTurnIsPlayedFromTheServersPosition() throws Exception {
        String id = startRun();
        // The entrance is the top-left cell, so north is always a wall and the turn is not used.
        mvc.perform(post("/api/runs/" + id + "/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"turn\": 0, \"action\": \"NORTH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turn").value(0))
                .andExpect(jsonPath("$.player.row").value(0));
    }

    @Test
    void anUnknownActionIsABadRequest() throws Exception {
        String id = startRun();
        mvc.perform(post("/api/runs/" + id + "/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"turn\": 0, \"action\": \"TELEPORT\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownRunIsNotFound() throws Exception {
        mvc.perform(get("/api/runs/no-such-run")).andExpect(status().isNotFound());
    }

    @Test
    void playPageForwardsToTheReactApp() throws Exception {
        mvc.perform(get("/play")).andExpect(forwardedUrl("/play/index.html"));
    }
}
