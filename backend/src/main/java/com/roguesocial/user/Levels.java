package com.roguesocial.user;

/**
 * How much exp each level takes, and what it gives. Level 2 takes 20 exp, and each level after
 * takes 1% more exp than the one before, rounded up: 20, 21, 22, 23... more, so level 3 is at 41
 * exp total and level 4 at 63.
 */
public final class Levels {

    /** What each level after the first adds to the player's base stats ({@link User#stats()}). */
    public static final int POWER_PER_LEVEL = 1;
    public static final int DEFENSE_PER_LEVEL = 1;
    public static final int HEALTH_PER_LEVEL = 2;

    private static final long FIRST_STEP = 20;

    private Levels() {
    }

    /** The total exp needed to reach {@code level}. Level 1 needs none. */
    public static long expFor(int level) {
        long total = 0;
        long step = FIRST_STEP;
        for (int l = 2; l <= level; l++) {
            total += step;
            step = next(step);
        }
        return total;
    }

    /** The level a player with {@code exp} total exp has reached. */
    public static int levelFor(long exp) {
        int level = 1;
        long total = 0;
        long step = FIRST_STEP;
        while (exp >= total + step) {
            total += step;
            step = next(step);
            level++;
        }
        return level;
    }

    /** 1% more, rounded up, in whole numbers so there's no floating-point drift. */
    private static long next(long step) {
        return (step * 101 + 99) / 100;
    }
}
