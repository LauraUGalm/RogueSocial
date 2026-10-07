package com.roguesocial.dungeon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.roguesocial.dungeon.RunView.Tile;

@Service
public class RunService {

    private static final Comparator<Tile> READING_ORDER =
            Comparator.comparingInt(Tile::row).thenComparingInt(Tile::col);

    private final FloorGenerator floors;
    private final RunStore store;
    private final Random random;

    public RunService(FloorGenerator floors, RunStore store, Random random) {
        this.floors = floors;
        this.store = store;
        this.random = random;
    }

    public RunView start() {
        Run run = new Run(UUID.randomUUID().toString(), floors.generate(random));
        run.see(LineOfSight.visible(run.floor(), run.player()));
        store.save(run);
        return view(run, true, List.of());
    }

    /** The run as it stands, with the player's whole map, for picking up where they left off. */
    public RunView resume(String runId) {
        Run run = find(runId);
        synchronized (run) {
            return view(run, true, List.of());
        }
    }

    /**
     * Plays one turn. {@code turn} must match the run's current turn, so a replayed or
     * out-of-order request is rejected rather than applied twice. Walking into a wall does not
     * use up a turn.
     */
    public RunView act(String runId, int turn, Action action) {
        Run run = find(runId);
        synchronized (run) {
            if (run.status() != Run.Status.ACTIVE) {
                throw new RunOverException(run.status());
            }
            if (turn != run.turn()) {
                throw new StaleTurnException(run.turn());
            }

            Floor floor = run.floor();
            Pos next = run.player().step(action);
            List<String> messages = new ArrayList<>();
            if (floor.isWall(next)) {
                return view(run, false, messages);
            }

            run.moveTo(next);
            int gold = floor.takeGold(next);
            if (gold > 0) {
                run.addGold(gold);
                messages.add("You pick up " + gold + " gold.");
            }
            if (next.equals(floor.exit())) {
                run.escape();
                messages.add("You escape with " + run.carriedGold() + " gold.");
            }
            run.endTurn();
            run.see(LineOfSight.visible(floor, next));
            store.save(run);
            return view(run, false, messages);
        }
    }

    private Run find(String runId) {
        return store.find(runId).orElseThrow(() -> new NoSuchElementException("No run " + runId));
    }

    private static RunView view(Run run, boolean withMap, List<String> messages) {
        Floor floor = run.floor();
        Pos origin = floor.entrance();
        List<Tile> visible = tiles(floor, origin, LineOfSight.visible(floor, run.player()));
        List<Tile> remembered = withMap ? tiles(floor, origin, run.seen()) : null;
        return new RunView(run.id(), run.turn(), run.status(), run.carriedGold(),
                run.player().minus(origin), visible, remembered, messages);
    }

    private static List<Tile> tiles(Floor floor, Pos origin, Collection<Pos> blocks) {
        return blocks.stream()
                .map(p -> {
                    char terrain = floor.isWall(p) ? '#' : p.equals(floor.exit()) ? '>' : '.';
                    int gold = floor.goldAt(p);
                    Pos rel = p.minus(origin);
                    return new Tile(rel.row(), rel.col(), terrain, gold > 0 ? gold : null);
                })
                .sorted(READING_ORDER)
                .toList();
    }

    /** The request was for a turn other than the current one. */
    public static class StaleTurnException extends RuntimeException {
        private final int currentTurn;

        StaleTurnException(int currentTurn) {
            super("Expected turn " + currentTurn);
            this.currentTurn = currentTurn;
        }

        public int currentTurn() {
            return currentTurn;
        }
    }

    /** The run has ended and takes no more turns. */
    public static class RunOverException extends RuntimeException {
        RunOverException(Run.Status status) {
            super("This run is over: " + status);
        }
    }
}
