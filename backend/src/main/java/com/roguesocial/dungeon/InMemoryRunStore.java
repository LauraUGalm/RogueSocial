package com.roguesocial.dungeon;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** Keeps runs in memory. Everything is lost when the server restarts. */
@Component
public class InMemoryRunStore implements RunStore {

    private final Map<String, Run> runs = new ConcurrentHashMap<>();

    @Override
    public Optional<Run> find(String id) {
        return Optional.ofNullable(runs.get(id));
    }

    @Override
    public void save(Run run) {
        runs.put(run.id(), run);
    }
}
