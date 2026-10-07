package com.roguesocial.dungeon;

/**
 * What the player wants to do this turn. The browser sends only this, never a position:
 * the server works out the result from the position it already holds.
 */
public enum Action {
    NORTH(-1, 0),
    SOUTH(1, 0),
    WEST(0, -1),
    EAST(0, 1),
    /** Leave the dungeon and bank the gold carried. Only works standing on the exit. */
    LEAVE(0, 0);

    private final int dRow;
    private final int dCol;

    Action(int dRow, int dCol) {
        this.dRow = dRow;
        this.dCol = dCol;
    }

    public int dRow() {
        return dRow;
    }

    public int dCol() {
        return dCol;
    }
}
