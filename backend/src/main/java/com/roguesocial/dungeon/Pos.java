package com.roguesocial.dungeon;

/** A block on a floor's grid. Row 0 is the top. */
public record Pos(int row, int col) {

    public Pos step(Action action) {
        return new Pos(row + action.dRow(), col + action.dCol());
    }

    public Pos minus(Pos other) {
        return new Pos(row - other.row, col - other.col);
    }
}
