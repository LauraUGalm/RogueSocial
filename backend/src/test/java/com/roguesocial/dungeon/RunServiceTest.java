package com.roguesocial.dungeon;

import static com.roguesocial.dungeon.Action.EAST;
import static com.roguesocial.dungeon.Action.NORTH;
import static com.roguesocial.dungeon.Action.SOUTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.roguesocial.dungeon.RunService.RunOverException;
import com.roguesocial.dungeon.RunService.StaleTurnException;
import com.roguesocial.dungeon.RunView.Tile;

class RunServiceTest {

    //   #######
    //   #..>..#   exit at (1,3)
    //   #.#####
    //   #.$...#   entrance at (3,1), 10 gold at (3,2)
    //   #######
    /** A bank that just remembers deposits. */
    private final Map<String, Long> deposits = new HashMap<>();
    private final Bank bank = (username, gold) -> deposits.merge(username, (long) gold, Long::sum);

    private final RunService service = new RunService(new FloorGenerator(null) {
        @Override
        public Floor generate(Random random) {
            return TestFloors.floor(List.of(
                    "#######",
                    "#.....#",
                    "#.#####",
                    "#.....#",
                    "#######"),
                    new Pos(3, 1), new Pos(1, 3), Map.of(new Pos(3, 2), 10));
        }
    }, new InMemoryRunStore(), bank, new Random(), "laura");

    @Test
    void startsAtTheEntranceWhichIsTheOrigin() {
        RunView view = service.start();

        assertThat(view.player()).isEqualTo(new Pos(0, 0));
        assertThat(view.turn()).isZero();
        assertThat(view.status()).isEqualTo(Run.Status.ACTIVE);
        assertThat(view.remembered()).isNotEmpty();
        assertThat(view.visible()).contains(new Tile(0, 1, '.', 10));
    }

    @Test
    void positionsAreRelativeToTheEntrance() {
        String id = service.start().runId();

        RunView view = service.act(id, 0, NORTH);

        assertThat(view.player()).isEqualTo(new Pos(-1, 0));
        assertThat(view.remembered()).isNull();
    }

    @Test
    void walkingIntoAWallDoesNotUseATurn() {
        String id = service.start().runId();

        RunView view = service.act(id, 0, SOUTH);

        assertThat(view.player()).isEqualTo(new Pos(0, 0));
        assertThat(view.turn()).isZero();
    }

    @Test
    void steppingOnGoldPicksItUp() {
        String id = service.start().runId();

        RunView view = service.act(id, 0, EAST);

        assertThat(view.gold()).isEqualTo(10);
        assertThat(view.messages()).containsExactly("You pick up 10 gold.");
        assertThat(view.visible()).contains(new Tile(0, 1, '.', null));
    }

    @Test
    void rejectsAReplayedTurn() {
        String id = service.start().runId();
        service.act(id, 0, EAST);

        assertThatThrownBy(() -> service.act(id, 0, EAST))
                .isInstanceOf(StaleTurnException.class)
                .extracting(e -> ((StaleTurnException) e).currentTurn())
                .isEqualTo(1);
        assertThat(service.resume(id).gold()).isEqualTo(10);
    }

    /** Picks up the 10 gold, then walks to the exit. Returns the run ID. */
    private String walkToTheExitWithGold() {
        String id = service.start().runId();
        service.act(id, 0, EAST);
        service.act(id, 1, Action.WEST);
        service.act(id, 2, NORTH);
        service.act(id, 3, NORTH);
        service.act(id, 4, EAST);
        service.act(id, 5, EAST);
        return id;
    }

    @Test
    void reachingTheExitDoesNotEndTheRun() {
        String id = walkToTheExitWithGold();

        RunView view = service.resume(id);

        assertThat(view.status()).isEqualTo(Run.Status.ACTIVE);
        assertThat(view.onExit()).isTrue();
        assertThat(deposits).isEmpty();
        assertThat(service.act(id, 6, Action.WEST).onExit()).isFalse();
    }

    @Test
    void leavingAtTheExitBanksTheGoldAndEndsTheRun() {
        String id = walkToTheExitWithGold();

        RunView view = service.act(id, 6, Action.LEAVE);

        assertThat(view.status()).isEqualTo(Run.Status.LEFT);
        assertThat(view.messages()).containsExactly(
                "You leave the dungeon and bank 10 gold. Your bank now holds 10 gold.");
        assertThat(deposits).containsEntry("laura", 10L);
        assertThatThrownBy(() -> service.act(id, 7, Action.LEAVE)).isInstanceOf(RunOverException.class);
        assertThat(deposits).containsEntry("laura", 10L);
    }

    @Test
    void cannotLeaveAwayFromTheExit() {
        String id = service.start().runId();

        RunView view = service.act(id, 0, Action.LEAVE);

        assertThat(view.status()).isEqualTo(Run.Status.ACTIVE);
        assertThat(view.turn()).isZero();
        assertThat(view.messages()).containsExactly("There is no way out here.");
        assertThat(deposits).isEmpty();
    }

    @Test
    void ifTheBankFailsTheRunCarriesOn() {
        RunService failing = new RunService(new FloorGenerator(null) {
            @Override
            public Floor generate(Random random) {
                return TestFloors.floor(List.of("###", "#.#", "###"),
                        new Pos(1, 1), new Pos(1, 1), Map.of());
            }
        }, new InMemoryRunStore(), (u, g) -> {
            throw new IllegalStateException("database is down");
        }, new Random(), "laura");
        String id = failing.start().runId();

        assertThatThrownBy(() -> failing.act(id, 0, Action.LEAVE))
                .isInstanceOf(IllegalStateException.class);
        assertThat(failing.resume(id).status()).isEqualTo(Run.Status.ACTIVE);
        assertThat(failing.resume(id).turn()).isZero();
    }

    @Test
    void resumeReturnsTheWholeMap() {
        String id = service.start().runId();
        service.act(id, 0, NORTH);
        service.act(id, 1, NORTH);

        RunView view = service.resume(id);

        assertThat(view.player()).isEqualTo(new Pos(-2, 0));
        assertThat(view.remembered()).hasSizeGreaterThan(view.visible().size());
    }

    @Test
    void unknownRunIsNotFound() {
        assertThatThrownBy(() -> service.resume("nope")).isInstanceOf(NoSuchElementException.class);
    }
}
