package com.roguesocial.dungeon;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What the browser is told after a turn. Positions are relative to the floor's entrance, so the
 * browser never learns the floor's true size or where its edges are.
 *
 * @param health     the player's health right now
 * @param stats      the player's level, power, defense and maximum health
 * @param onExit     whether the player is standing on the exit, and so can leave
 * @param monsters   the monsters the player can see right now
 * @param monstersWentFirst who won this turn's coin flip; omitted when no turn was played
 * @param visible    blocks the player can see right now
 * @param remembered every block the player has seen on this floor; sent only when resuming a run
 * @param messages   what happened this turn, such as gold picked up
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RunView(
        String runId,
        int turn,
        Run.Status status,
        int gold,
        Pos player,
        int health,
        PlayerStats.Stats stats,
        boolean onExit,
        List<Tile> visible,
        List<SeenMonster> monsters,
        Boolean monstersWentFirst,
        List<Tile> remembered,
        List<String> messages) {

    /**
     * One block of the floor.
     *
     * @param terrain '#' wall, '.' floor, '>' exit
     * @param gold    gold lying here, omitted when there is none
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Tile(int row, int col, char terrain, Integer gold) {
    }

    /** A monster in sight. {@code kind} is its sprite name, such as "bat". */
    public record SeenMonster(int row, int col, String kind, int health, int maxHealth) {
    }
}
