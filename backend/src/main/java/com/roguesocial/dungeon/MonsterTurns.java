package com.roguesocial.dungeon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * What the monsters do on their half of a turn. Every monster acts, seen or not: one engaged with
 * the player attacks, one that can see the player closes in, and the rest wander.
 *
 * <p>Only engaged monsters attack: ones already next to the player when the turn began, on a turn
 * the player stands and fights. Stepping up to a monster, or away from one, is never punished.
 */
final class MonsterTurns {

    private static final List<Action> MOVES = List.of(Action.NORTH, Action.SOUTH, Action.EAST, Action.WEST);

    private MonsterTurns() {
    }

    /** The monsters next to the player now: the ones that may attack if the player stands and fights. */
    static Set<Monster> engaged(Run run) {
        return Set.copyOf(run.floor().monsters().stream()
                .filter(m -> adjacent(m.pos(), run.player()))
                .toList());
    }

    /** Plays every monster's move. Stops early if the player dies. */
    static void play(Run run, Set<Monster> engaged, Random random, List<String> messages) {
        Floor floor = run.floor();
        Map<Pos, Integer> stepsToPlayer = null;
        for (Monster m : List.copyOf(floor.monsters())) {
            if (adjacent(m.pos(), run.player()) && !engaged.contains(m)) {
                continue; // The player just stepped up to it: it squares up but doesn't strike yet.
            }
            if (engaged.contains(m) && adjacent(m.pos(), run.player())) {
                int damage = Combat.damage(m.type().strength(), run.stats().defense());
                run.hurt(damage);
                messages.add("The " + m.type().sprite() + " " + m.type().verb() + " you for "
                        + damage + ".");
                if (run.health() == 0) {
                    messages.add("The " + m.type().sprite() + " kills you.");
                    return;
                }
            } else if (LineOfSight.visible(floor, m.pos()).contains(run.player())) {
                if (stepsToPlayer == null) {
                    stepsToPlayer = distances(floor, run.player());
                }
                closeIn(run, m, stepsToPlayer);
            } else {
                wander(run, m, random);
            }
        }
    }

    /** Steps to a free neighbouring block that is fewer steps from the player. */
    private static void closeIn(Run run, Monster m, Map<Pos, Integer> stepsToPlayer) {
        int best = stepsToPlayer.getOrDefault(m.pos(), Integer.MAX_VALUE);
        Pos to = null;
        for (Action a : MOVES) {
            Pos n = m.pos().step(a);
            Integer d = stepsToPlayer.get(n);
            if (d != null && d < best && free(run, n)) {
                best = d;
                to = n;
            }
        }
        if (to != null) {
            m.moveTo(to);
        }
    }

    /** Steps to a random free neighbouring block, if there is one. */
    private static void wander(Run run, Monster m, Random random) {
        List<Pos> options = new ArrayList<>();
        for (Action a : MOVES) {
            Pos n = m.pos().step(a);
            if (free(run, n)) {
                options.add(n);
            }
        }
        if (!options.isEmpty()) {
            m.moveTo(options.get(random.nextInt(options.size())));
        }
    }

    /** Open floor with no monster and no player on it. */
    private static boolean free(Run run, Pos p) {
        return !run.floor().isWall(p) && !p.equals(run.player()) && run.floor().monsterAt(p).isEmpty();
    }

    static boolean adjacent(Pos a, Pos b) {
        return Math.abs(a.row() - b.row()) + Math.abs(a.col() - b.col()) == 1;
    }

    /** Steps from every reachable block to {@code target}, ignoring monsters in the way. */
    private static Map<Pos, Integer> distances(Floor floor, Pos target) {
        Map<Pos, Integer> dist = new HashMap<>();
        Deque<Pos> todo = new ArrayDeque<>();
        dist.put(target, 0);
        todo.add(target);
        while (!todo.isEmpty()) {
            Pos p = todo.poll();
            for (Action a : MOVES) {
                Pos n = p.step(a);
                if (!floor.isWall(n) && !dist.containsKey(n)) {
                    dist.put(n, dist.get(p) + 1);
                    todo.add(n);
                }
            }
        }
        return dist;
    }
}
