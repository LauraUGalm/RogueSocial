package com.roguesocial.dungeon;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/** Keeps runs in memory. Everything is lost when the server restarts. */
@Component
public class InMemoryRunStore implements RunStore {

    // Kept in the order they were last saved, oldest first.
    private final Map<String, Run> runs = Collections.synchronizedMap(new LinkedHashMap<>());

    @Override
    public Optional<Run> find(String id) {
        return Optional.ofNullable(runs.get(id));
    }

    @Override
    public void save(Run run) {
        synchronized (runs) {
            runs.remove(run.id());
            runs.put(run.id(), run);
        }
    }

    @Override
    public List<Run> all() {
        synchronized (runs) {
            return runs.values().stream().toList().reversed();
        }
    }
}
