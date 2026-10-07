package com.roguesocial.dungeon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.roguesocial.dungeon.RunView.SeenMonster;
import com.roguesocial.dungeon.RunView.Tile;

@Service
public class RunService {

    private static final Comparator<Tile> READING_ORDER =
            Comparator.comparingInt(Tile::row).thenComparingInt(Tile::col);

    private final FloorGenerator floors;
    private final RunStore store;
    private final Bank bank;
    private final PlayerStats stats;
    private final Random random;
    // Until there is sign-in, every run belongs to this one player.
    private final String player;

    public RunService(FloorGenerator floors, RunStore store, Bank bank, PlayerStats stats,
            Random random, @Value("${rogue.player}") String player) {
        this.floors = floors;
        this.store = store;
        this.bank = bank;
        this.stats = stats;
        this.random = random;
        this.player = player;
    }

    public RunView start() {
        Run run = new Run(UUID.randomUUID().toString(), player, stats.lookup(player),
                floors.generate(random));
        run.see(LineOfSight.visible(run.floor(), run.player()));
        store.save(run);
        return view(run, true, List.of(), null);
    }

    /** The run as it stands, with the player's whole map, for picking up where they left off. */
    public RunView resume(String runId) {
        Run run = find(runId);
        synchronized (run) {
            return view(run, true, List.of(), null);
        }
    }

    /**
     * Plays one turn. {@code turn} must match the run's current turn, so a replayed or
     * out-of-order request is rejected rather than applied twice. Walking into a wall, or trying
     * to leave anywhere but the exit, does not use up a turn.
     *
     * <p>A coin flip decides whether the player or the monsters go first. Walking into a monster
     * attacks it, and only then do the monsters beside the player strike back: stepping up to a
     * monster or away from one is safe. Leaving happens before the monsters move.
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

            Pos playerBefore = run.player();
            int healthBefore = run.health();
            List<TurnRecord.MonsterAt> monstersBefore = TurnRecord.monsters(run);
            RunView view = play(run, action);
            String result = view.monstersWentFirst() != null ? "played"
                    : action == Action.LEAVE && view.status() == Run.Status.ACTIVE ? "no way out"
                    : action == Action.LEAVE ? "left" : "blocked";
            run.record(new TurnRecord(turn, action, result, view.monstersWentFirst(),
                    playerBefore, run.player(), healthBefore, run.health(),
                    monstersBefore, TurnRecord.monsters(run), view.messages()));
            return view;
        }
    }

    private RunView play(Run run, Action action) {
        Floor floor = run.floor();
        List<String> messages = new ArrayList<>();
        if (action == Action.LEAVE) {
            return leave(run, messages);
        }
        if (floor.isWall(run.player().step(action))) {
            return view(run, false, messages, null);
        }

        // Monsters only strike when the player stands and fights: attacking, not stepping.
        boolean fighting = floor.monsterAt(run.player().step(action)).isPresent();
        Set<Monster> engaged = fighting ? MonsterTurns.engaged(run) : Set.of();

        boolean monstersFirst = random.nextBoolean();
        if (monstersFirst) {
            MonsterTurns.play(run, engaged, random, messages);
        }
        if (run.health() > 0) {
            playerMoves(run, action, messages);
            if (!monstersFirst) {
                MonsterTurns.play(run, engaged, random, messages);
            }
        }
        if (run.health() == 0) {
            run.die();
            messages.add("You die. The " + run.carriedGold() + " gold you carried is lost.");
        }
        run.endTurn();
        run.see(LineOfSight.visible(floor, run.player()));
        store.save(run);
        return view(run, false, messages, monstersFirst);
    }

    /** Moves the player one step, or attacks the monster standing there. */
    private static void playerMoves(Run run, Action action, List<String> messages) {
        Floor floor = run.floor();
        Pos next = run.player().step(action);
        Monster target = floor.monsterAt(next).orElse(null);
        if (target != null) {
            int damage = Combat.damage(run.stats().power(), target.type().defense());
            target.hurt(damage);
            String name = target.type().sprite();
            if (target.isDead()) {
                floor.remove(target);
                messages.add("You hit the " + name + " for " + damage + ". The " + name + " dies.");
            } else {
                messages.add("You hit the " + name + " for " + damage + ".");
            }
            return;
        }

        run.moveTo(next);
        int gold = floor.takeGold(next);
        if (gold > 0) {
            run.addGold(gold);
            messages.add("You pick up " + gold + " gold.");
        }
        if (next.equals(floor.exit())) {
            messages.add("You find the way out. Leave now to bank your gold, or keep exploring.");
        }
    }

    /**
     * Banks the gold carried and ends the run. The bank is credited first: if that fails, the run
     * carries on as it was, so the gold is never lost or banked twice.
     */
    private RunView leave(Run run, List<String> messages) {
        if (!run.player().equals(run.floor().exit())) {
            messages.add("There is no way out here.");
            return view(run, false, messages, null);
        }
        long banked = bank.depositGold(run.owner(), run.carriedGold());
        run.leave();
        run.endTurn();
        store.save(run);
        messages.add("You leave the dungeon and bank " + run.carriedGold() + " gold. "
                + "Your bank now holds " + banked + " gold.");
        return view(run, false, messages, null);
    }

    private Run find(String runId) {
        return store.find(runId).orElseThrow(() -> new NoSuchElementException("No run " + runId));
    }

    private static RunView view(Run run, boolean withMap, List<String> messages,
            Boolean monstersFirst) {
        Floor floor = run.floor();
        Pos origin = floor.entrance();
        Set<Pos> inSight = LineOfSight.visible(floor, run.player());
        List<Tile> visible = tiles(floor, origin, inSight);
        List<Tile> remembered = withMap ? tiles(floor, origin, run.seen()) : null;
        List<SeenMonster> monsters = floor.monsters().stream()
                .filter(m -> inSight.contains(m.pos()))
                .map(m -> {
                    Pos rel = m.pos().minus(origin);
                    return new SeenMonster(rel.row(), rel.col(), m.type().sprite(), m.health(),
                            m.type().maxHealth());
                })
                .toList();
        return new RunView(run.id(), run.turn(), run.status(), run.carriedGold(),
                run.player().minus(origin), run.health(), run.stats(),
                run.player().equals(floor.exit()), visible, monsters, monstersFirst, remembered,
                messages);
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
