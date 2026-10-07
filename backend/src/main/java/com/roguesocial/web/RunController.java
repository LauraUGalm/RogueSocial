package com.roguesocial.web;

import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.roguesocial.dungeon.Action;
import com.roguesocial.dungeon.RunService;
import com.roguesocial.dungeon.RunService.RunOverException;
import com.roguesocial.dungeon.RunService.StaleTurnException;
import com.roguesocial.dungeon.RunView;

@RestController
@RequestMapping("/api/runs")
public class RunController {

    /** The body of a turn: which turn it is for, and what the player wants to do. */
    public record TurnRequest(int turn, Action action) {
    }

    private final RunService runs;

    public RunController(RunService runs) {
        this.runs = runs;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RunView start() {
        return runs.start();
    }

    @GetMapping("/{id}")
    public RunView resume(@PathVariable String id) {
        return runs.resume(id);
    }

    @PostMapping("/{id}/turns")
    public RunView act(@PathVariable String id, @RequestBody TurnRequest request) {
        if (request.action() == null) {
            throw new IllegalArgumentException("Missing action");
        }
        return runs.act(id, request.turn(), request.action());
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound(NoSuchElementException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(StaleTurnException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> staleTurn(StaleTurnException e) {
        return Map.of("error", e.getMessage(), "currentTurn", e.currentTurn());
    }

    @ExceptionHandler(RunOverException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> runOver(RunOverException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }
}
