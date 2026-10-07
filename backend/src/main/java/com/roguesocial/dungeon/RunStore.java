package com.roguesocial.dungeon;

import java.util.List;
import java.util.Optional;

/** Where runs are saved between turns. In memory for now; DynamoDB once the tables are designed. */
public interface RunStore {

    Optional<Run> find(String id);

    void save(Run run);

    /** Every stored run, most recently saved first. For debugging. */
    List<Run> all();
}
