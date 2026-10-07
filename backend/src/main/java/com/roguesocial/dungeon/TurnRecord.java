package com.roguesocial.dungeon;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One request to a run, kept on the server for debugging. Positions are true floor positions,
 * not relative to the entrance, and every monster is listed, seen or not.
 *
 * @param result      "played", "blocked" (a wall, no turn used) or "no way out" (LEAVE off the exit)
 * @param monstersFirst who won the coin flip; null when no turn was played
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TurnRecord(
        int turn,
        Action action,
        String result,
        Boolean monstersFirst,
        Pos playerBefore,
        Pos playerAfter,
        int healthBefore,
        int healthAfter,
        List<MonsterAt> monstersBefore,
        List<MonsterAt> monstersAfter,
        List<String> messages) {

    /** A monster where it stood, and whether the player could see it. */
    public record MonsterAt(String kind, int row, int col, int health, boolean inSight) {
    }

    static List<MonsterAt> monsters(Run run) {
        var inSight = LineOfSight.visible(run.floor(), run.player());
        return run.floor().monsters().stream()
                .map(m -> new MonsterAt(m.type().sprite(), m.pos().row(), m.pos().col(), m.health(),
                        inSight.contains(m.pos())))
                .toList();
    }
}
