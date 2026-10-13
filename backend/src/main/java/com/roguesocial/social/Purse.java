package com.roguesocial.social;

/** Where the gold for posts comes from: the player's bank, in Postgres. */
public interface Purse {

    /**
     * Takes {@code gold} from the player's bank, all of it or none.
     *
     * @return the player's banked gold afterwards
     * @throws NotEnoughGoldException if they have less than {@code gold}
     */
    long spendGold(String username, int gold);

    /** Gives back gold taken by {@link #spendGold} when the post couldn't be saved. */
    void refundGold(String username, int gold);
}
