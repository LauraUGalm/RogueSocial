package com.roguesocial.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.roguesocial.dungeon.Floor;
import com.roguesocial.dungeon.Pos;
import com.roguesocial.dungeon.Run;
import com.roguesocial.dungeon.RunStore;
import com.roguesocial.dungeon.TurnRecord;

/**
 * For development only: shows whole floors and every turn, which would let a player cheat. Must
 * be removed or locked down before the site is public.
 */
@RestController
@RequestMapping("/api/debug/runs")
public class DebugController {

    public record RunSummary(String runId, String owner, int turn, Run.Status status, int health) {
    }

    public record RunDebug(String runId, Run.Status status, Pos entrance, List<String> floor,
            List<TurnRecord> history) {
    }

    private final RunStore runs;

    public DebugController(RunStore runs) {
        this.runs = runs;
    }

    /** Runs since the server started, most recently played first. */
    @GetMapping
    public List<RunSummary> list() {
        return runs.all().stream()
                .map(r -> new RunSummary(r.id(), r.owner(), r.turn(), r.status(), r.health()))
                .toList();
    }

    @GetMapping("/{id}")
    public RunDebug show(@PathVariable String id) {
        Run run = runs.find(id).orElseThrow(() -> new NoSuchElementException("No run " + id));
        synchronized (run) {
            return new RunDebug(run.id(), run.status(), run.floor().entrance(), floorText(run),
                    List.copyOf(run.history()));
        }
    }

    /** The whole floor now: '@' player, first letter of each monster, '$' gold, '>' exit. */
    private static List<String> floorText(Run run) {
        Floor f = run.floor();
        List<String> rows = new ArrayList<>();
        for (int r = 0; r < f.rows(); r++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < f.cols(); c++) {
                Pos p = new Pos(r, c);
                var monster = f.monsterAt(p);
                sb.append(p.equals(run.player()) ? '@'
                        : monster.isPresent() ? monster.get().type().sprite().charAt(0)
                        : p.equals(f.exit()) ? '>'
                        : f.isWall(p) ? '#'
                        : f.goldAt(p) > 0 ? '$' : '.');
            }
            rows.add(sb.toString());
        }
        return rows;
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound(NoSuchElementException e) {
        return Map.of("error", e.getMessage());
    }
}
