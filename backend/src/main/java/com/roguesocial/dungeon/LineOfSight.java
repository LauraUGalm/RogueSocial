package com.roguesocial.dungeon;

import java.util.HashSet;
import java.util.Set;

/**
 * What the player can see: every block within {@link #RADIUS} that a straight line from the
 * player reaches without passing through a wall. Walls themselves are seen, but block what
 * lies behind them.
 */
public final class LineOfSight {

    public static final int RADIUS = 6;

    private LineOfSight() {
    }

    public static Set<Pos> visible(Floor floor, Pos from) {
        Set<Pos> seen = new HashSet<>();
        for (int dr = -RADIUS; dr <= RADIUS; dr++) {
            for (int dc = -RADIUS; dc <= RADIUS; dc++) {
                if (dr * dr + dc * dc > RADIUS * RADIUS) {
                    continue;
                }
                Pos to = new Pos(from.row() + dr, from.col() + dc);
                if (inBounds(floor, to) && clearPath(floor, from, to)) {
                    seen.add(to);
                }
            }
        }
        return seen;
    }

    /** Walks a Bresenham line from {@code from} to {@code to}; only the blocks in between must be open. */
    private static boolean clearPath(Floor floor, Pos from, Pos to) {
        int r = from.row(), c = from.col();
        int dr = Math.abs(to.row() - r), dc = Math.abs(to.col() - c);
        int sr = Integer.signum(to.row() - r), sc = Integer.signum(to.col() - c);
        int err = dc - dr;
        while (r != to.row() || c != to.col()) {
            if (!(r == from.row() && c == from.col()) && floor.isWall(new Pos(r, c))) {
                return false;
            }
            int e2 = 2 * err;
            if (e2 > -dr) {
                err -= dr;
                c += sc;
            }
            if (e2 < dc) {
                err += dc;
                r += sr;
            }
        }
        return true;
    }

    private static boolean inBounds(Floor floor, Pos p) {
        return p.row() >= 0 && p.row() < floor.rows() && p.col() >= 0 && p.col() < floor.cols();
    }
}
