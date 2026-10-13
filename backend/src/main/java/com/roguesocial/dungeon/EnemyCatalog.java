package com.roguesocial.dungeon;

import java.util.NoSuchElementException;

/** Where floors get the stats of the monsters they place. */
public interface EnemyCatalog {

    /** @throws NoSuchElementException if there is no such enemy */
    EnemyType byName(String name);
}
