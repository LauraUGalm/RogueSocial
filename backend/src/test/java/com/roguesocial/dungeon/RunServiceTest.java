package com.roguesocial.dungeon;

import static com.roguesocial.dungeon.Action.EAST;
import static com.roguesocial.dungeon.Action.NORTH;
import static com.roguesocial.dungeon.Action.SOUTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    }, new InMemoryRunStore(), new Random());

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

    @Test
    void reachingTheExitEndsTheRun() {
        String id = service.start().runId();
        service.act(id, 0, EAST);
        service.act(id, 1, Action.WEST);
        service.act(id, 2, NORTH);
        service.act(id, 3, NORTH);
        service.act(id, 4, EAST);

        RunView view = service.act(id, 5, EAST);

        assertThat(view.status()).isEqualTo(Run.Status.ESCAPED);
        assertThat(view.messages()).contains("You escape with 10 gold.");
        assertThatThrownBy(() -> service.act(id, 6, EAST)).isInstanceOf(RunOverException.class);
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
