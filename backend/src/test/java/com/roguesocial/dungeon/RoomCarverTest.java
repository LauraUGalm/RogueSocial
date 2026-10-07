package com.roguesocial.dungeon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import com.roguesocial.dungeon.RoomCarver.Room;
import com.roguesocial.maze.WilsonMazeGenerator;

class RoomCarverTest {

    private final FloorGenerator generator = new FloorGenerator(new WilsonMazeGenerator());

    @RepeatedTest(50)
    void keepsTheOuterWallAndEveryCellReachable() {
        Floor floor = generator.generate(new Random());

        for (int r = 0; r < floor.rows(); r++) {
            assertThat(floor.isWall(new Pos(r, 0))).isTrue();
            assertThat(floor.isWall(new Pos(r, floor.cols() - 1))).isTrue();
        }
        for (int c = 0; c < floor.cols(); c++) {
            assertThat(floor.isWall(new Pos(0, c))).isTrue();
            assertThat(floor.isWall(new Pos(floor.rows() - 1, c))).isTrue();
        }

        Set<Pos> reached = reachable(floor, floor.entrance());
        assertThat(reached).contains(floor.exit());
        for (int r = 1; r < floor.rows(); r += 2) {
            for (int c = 1; c < floor.cols(); c += 2) {
                assertThat(reached).contains(new Pos(r, c));
            }
        }
    }

    @Test
    void clearsTheInsideOfEachRoomButNotItsEdges() {
        boolean[][] walls = new boolean[23][31];
        for (boolean[] row : walls) {
            java.util.Arrays.fill(row, true);
        }

        List<Room> rooms = RoomCarver.carve(walls, 15, 11, new Random(7));

        assertThat(rooms).isNotEmpty().hasSizeLessThanOrEqualTo(RoomCarver.ROOMS);
        for (Room room : rooms) {
            int top = 2 * room.cy() + 1, bottom = 2 * (room.cy() + room.h()) - 1;
            int left = 2 * room.cx() + 1, right = 2 * (room.cx() + room.w()) - 1;
            for (int r = top; r <= bottom; r++) {
                for (int c = left; c <= right; c++) {
                    assertThat(walls[r][c]).as("inside %s at %d,%d", room, r, c).isFalse();
                }
                assertThat(walls[r][left - 1]).isTrue();
                assertThat(walls[r][right + 1]).isTrue();
            }
            for (int c = left; c <= right; c++) {
                assertThat(walls[top - 1][c]).isTrue();
                assertThat(walls[bottom + 1][c]).isTrue();
            }
        }
    }

    @RepeatedTest(20)
    void roomsDoNotMerge() {
        List<Room> rooms = RoomCarver.carve(new boolean[23][31], 15, 11, new Random());
        for (int i = 0; i < rooms.size(); i++) {
            for (int j = i + 1; j < rooms.size(); j++) {
                assertThat(rooms.get(i).tooClose(rooms.get(j))).isFalse();
            }
        }
    }

    private static Set<Pos> reachable(Floor floor, Pos from) {
        Set<Pos> seen = new HashSet<>();
        Deque<Pos> todo = new ArrayDeque<>();
        seen.add(from);
        todo.add(from);
        while (!todo.isEmpty()) {
            Pos p = todo.poll();
            for (Action a : List.of(Action.NORTH, Action.SOUTH, Action.EAST, Action.WEST)) {
                Pos n = p.step(a);
                if (!floor.isWall(n) && seen.add(n)) {
                    todo.add(n);
                }
            }
        }
        return seen;
    }
}
