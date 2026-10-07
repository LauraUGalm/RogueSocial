package com.roguesocial.dungeon;

import static com.roguesocial.dungeon.Action.EAST;
import static com.roguesocial.dungeon.Action.SOUTH;
import static com.roguesocial.dungeon.Action.WEST;
import static com.roguesocial.dungeon.MonsterType.BAT;
import static com.roguesocial.dungeon.MonsterType.SCORPION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import com.roguesocial.dungeon.RunService.RunOverException;

class CombatTest {

    private final List<Integer> deposits = new ArrayList<>();

    @Test
    void damageIsStrengthMinusDefenseButAtLeastOne() {
        assertThat(Combat.damage(BAT.strength(), 5)).isEqualTo(1);
        assertThat(Combat.damage(SCORPION.strength(), 5)).isEqualTo(2);
        assertThat(Combat.damage(5, BAT.defense())).isEqualTo(4);
        assertThat(Combat.damage(5, SCORPION.defense())).isEqualTo(2);
        assertThat(Combat.damage(1, 100)).isEqualTo(1);
    }

    @Test
    void monsterStats() {
        assertThat(List.of(BAT.maxHealth(), BAT.strength(), BAT.defense())).containsExactly(3, 2, 1);
        assertThat(List.of(SCORPION.maxHealth(), SCORPION.strength(), SCORPION.defense()))
                .containsExactly(10, 7, 3);
    }

    //   #######
    //   #@....#   player at (1,1); monsters placed per test
    //   #.....#
    //   #######
    private RunService service(Random coins, PlayerStats.Stats stats, Monster... monsters) {
        return new RunService(TestFloors.always(() -> TestFloors.floor(
                List.of(
                        "#######",
                        "#.....#",
                        "#.....#",
                        "#######"),
                new Pos(1, 1), new Pos(2, 5), Map.of(), List.of(monsters))),
                new InMemoryRunStore(), (u, g) -> {
                    deposits.add(g);
                    return g;
                }, u -> stats, coins, "laura");
    }

    @Test
    void walkingIntoABatKillsItInOneHit() {
        RunService s = service(TestFloors.coins(false), TestFloors.STATS, new Monster(BAT, new Pos(1, 2)));
        String id = s.start().runId();

        RunView view = s.act(id, 0, EAST);

        assertThat(view.messages()).containsExactly("You hit the bat for 4. The bat dies.");
        assertThat(view.monsters()).isEmpty();
        assertThat(view.player()).isEqualTo(new Pos(0, 0));
        assertThat(view.health()).isEqualTo(20);
    }

    @Test
    void whenTheMonstersWinTheCoinFlipTheyStrikeFirst() {
        RunService s = service(TestFloors.coins(true), TestFloors.STATS, new Monster(BAT, new Pos(1, 2)));
        String id = s.start().runId();

        RunView view = s.act(id, 0, EAST);

        assertThat(view.messages()).containsExactly(
                "The bat bites you for 1.", "You hit the bat for 4. The bat dies.");
        assertThat(view.health()).isEqualTo(19);
        assertThat(view.monstersWentFirst()).isTrue();
    }

    @Test
    void aScorpionTakesFiveHitsAndStingsForTwo() {
        RunService s = service(TestFloors.coins(), TestFloors.STATS,
                new Monster(SCORPION, new Pos(1, 2)));
        String id = s.start().runId();

        RunView view = s.act(id, 0, EAST);
        assertThat(view.messages()).containsExactly("You hit the scorpion for 2.", "The scorpion stings you for 2.");
        assertThat(view.monsters()).singleElement()
                .satisfies(m -> assertThat(m.health()).isEqualTo(8));
        for (int turn = 1; turn < 5; turn++) {
            view = s.act(id, turn, EAST);
        }

        assertThat(view.messages()).containsExactly("You hit the scorpion for 2. The scorpion dies.");
        assertThat(view.monsters()).isEmpty();
        assertThat(view.health()).isEqualTo(12);
    }

    @Test
    void everyMonsterNextToThePlayerAttacks() {
        RunService s = service(TestFloors.coins(true), TestFloors.STATS,
                new Monster(BAT, new Pos(1, 2)), new Monster(SCORPION, new Pos(2, 1)));
        String id = s.start().runId();

        RunView view = s.act(id, 0, EAST);

        assertThat(view.messages()).containsExactly(
                "The bat bites you for 1.",
                "The scorpion stings you for 2.",
                "You hit the bat for 4. The bat dies.");
        assertThat(view.health()).isEqualTo(17);
        assertThat(view.monsters()).singleElement()
                .satisfies(m -> assertThat(m.kind()).isEqualTo("scorpion"));
    }

    @Test
    void dyingEndsTheRunAndLosesTheGold() {
        PlayerStats.Stats frail = new PlayerStats.Stats(1, 5, 5, 2);
        RunService s = service(TestFloors.coins(true), frail, new Monster(SCORPION, new Pos(2, 1)));
        String id = s.start().runId();

        RunView view = s.act(id, 0, SOUTH);

        assertThat(view.status()).isEqualTo(Run.Status.DIED);
        assertThat(view.health()).isZero();
        assertThat(view.player()).isEqualTo(new Pos(0, 0));
        assertThat(view.messages()).containsExactly(
                "The scorpion stings you for 2.", "The scorpion kills you.",
                "You die. The 0 gold you carried is lost.");
        assertThatThrownBy(() -> s.act(id, 1, WEST)).isInstanceOf(RunOverException.class);
        assertThat(deposits).isEmpty();
    }

    @Test
    void steppingUpToAMonsterIsSafe() {
        // The player wins the flip and steps next to the bat; it doesn't get a free bite.
        RunService s = service(TestFloors.coins(false), TestFloors.STATS, new Monster(BAT, new Pos(1, 3)));
        String id = s.start().runId();

        RunView view = s.act(id, 0, EAST);

        assertThat(view.health()).isEqualTo(20);
        assertThat(view.messages()).isEmpty();
        assertThat(view.monsters()).singleElement()
                .satisfies(m -> assertThat(m.col()).isEqualTo(2));
    }

    @Test
    void steppingAwayFromAMonsterIsSafe() {
        // The monsters win the flip while a scorpion is beside the player; the player steps away.
        RunService s = service(TestFloors.coins(true), TestFloors.STATS,
                new Monster(SCORPION, new Pos(2, 1)));
        String id = s.start().runId();

        RunView view = s.act(id, 0, EAST);

        assertThat(view.health()).isEqualTo(20);
        assertThat(view.messages()).isEmpty();
    }

    @RepeatedTest(20)
    void aPlayerWhoNeverAttacksIsNeverHit() {
        // The playtest of 2026-10-06: stepping up to a bat and backing off again, over and over,
        // gave it free bites. Now only standing and fighting does.
        Random random = new Random();
        RunService s = service(random, TestFloors.STATS, new Monster(BAT, new Pos(1, 4)));
        RunView view = s.start();
        String id = view.runId();

        for (int i = 0; i < 50; i++) {
            List<Action> steps = new ArrayList<>();
            for (Action a : List.of(Action.NORTH, SOUTH, EAST, WEST)) {
                int r = view.player().row() + a.dRow(), c = view.player().col() + a.dCol();
                boolean wall = r < 0 || r > 1 || c < 0 || c > 4;
                boolean bat = view.monsters().stream().anyMatch(m -> m.row() == r && m.col() == c);
                if (!wall && !bat) {
                    steps.add(a);
                }
            }
            if (steps.isEmpty()) {
                continue;
            }
            view = s.act(id, view.turn(), steps.get(random.nextInt(steps.size())));
        }

        assertThat(view.health()).isEqualTo(20);
    }

    @Test
    void aMonsterThatSeesThePlayerClosesIn() {
        RunService s = service(TestFloors.coins(), TestFloors.STATS, new Monster(BAT, new Pos(1, 5)));
        String id = s.start().runId();

        RunView view = s.act(id, 0, SOUTH);

        // The player is now at (1,0); the bat started 5 steps away and takes one step closer.
        assertThat(view.monsters()).singleElement().satisfies(m -> assertThat(
                Math.abs(m.row() - view.player().row()) + Math.abs(m.col() - view.player().col()))
                .isEqualTo(4));
    }

    @Test
    void walkingIntoAWallLetsNoMonsterMove() {
        RunService s = service(TestFloors.coins(true), TestFloors.STATS, new Monster(BAT, new Pos(1, 2)));
        String id = s.start().runId();

        RunView view = s.act(id, 0, Action.NORTH);

        assertThat(view.health()).isEqualTo(20);
        assertThat(view.turn()).isZero();
        assertThat(view.messages()).isEmpty();
        assertThat(view.monstersWentFirst()).isNull();
    }
}
