package com.roguesocial.dungeon;

import java.util.List;

/** Where floors get the stats of the monsters they place. */
public interface EnemyCatalog {

    /** Every kind of enemy a player of {@code level} can meet: those with a min level up to it. */
    List<EnemyType> upToLevel(int level);
}
