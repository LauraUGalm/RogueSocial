package com.roguesocial.dungeon;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What the browser is told after a turn. Positions are relative to the floor's entrance, so the
 * browser never learns the floor's true size or where its edges are.
 *
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
        List<Tile> visible,
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
}
