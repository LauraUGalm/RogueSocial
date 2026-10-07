package com.roguesocial.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Turns parts of a maze into rooms by clearing every wall inside a rectangle of cells. The walls
 * around a room stay, along with the outer wall of the floor. Removing walls only ever adds
 * paths, so a floor that was solvable stays solvable.
 */
final class RoomCarver {

    static final int ROOMS = 4;
    static final int MIN_SIZE = 2;
    static final int MAX_SIZE = 4;
    /** How many random spots to try before giving up on fitting another room. */
    private static final int ATTEMPTS = 50;

    /** A room in cell coordinates: {@code w x h} cells with its top-left cell at (cx, cy). */
    record Room(int cx, int cy, int w, int h) {

        /** Rooms that touch or sit side by side would merge into one, so keep a cell between them. */
        boolean tooClose(Room o) {
            return cx - 1 < o.cx + o.w && o.cx - 1 < cx + w
                    && cy - 1 < o.cy + o.h && o.cy - 1 < cy + h;
        }
    }

    private RoomCarver() {
    }

    /** Clears up to {@link #ROOMS} rooms in a block grid of {@code cellCols x cellRows} cells. */
    static List<Room> carve(boolean[][] walls, int cellCols, int cellRows, Random random) {
        List<Room> rooms = new ArrayList<>();
        for (int i = 0; i < ATTEMPTS && rooms.size() < ROOMS; i++) {
            int w = MIN_SIZE + random.nextInt(MAX_SIZE - MIN_SIZE + 1);
            int h = MIN_SIZE + random.nextInt(MAX_SIZE - MIN_SIZE + 1);
            if (w > cellCols || h > cellRows) {
                continue;
            }
            Room room = new Room(random.nextInt(cellCols - w + 1), random.nextInt(cellRows - h + 1), w, h);
            if (rooms.stream().noneMatch(room::tooClose)) {
                rooms.add(room);
                clear(walls, room);
            }
        }
        return rooms;
    }

    /** Opens every block from the room's first cell to its last; the blocks around them stay. */
    private static void clear(boolean[][] walls, Room room) {
        for (int r = 2 * room.cy() + 1; r <= 2 * (room.cy() + room.h()) - 1; r++) {
            for (int c = 2 * room.cx() + 1; c <= 2 * (room.cx() + room.w()) - 1; c++) {
                walls[r][c] = false;
            }
        }
    }
}
