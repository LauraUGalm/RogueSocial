package com.roguesocial.enemy;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.roguesocial.dungeon.EnemyCatalog;
import com.roguesocial.dungeon.EnemyType;

/** Reads the {@code enemies} table (db/schema.sql). */
@Repository
public class EnemyRepository implements EnemyCatalog {

    private static final String COLUMNS = "name, health, power, defense, exp, min_level";

    private static final RowMapper<EnemyType> ROW = (rs, n) -> new EnemyType(
            rs.getString("name"),
            rs.getInt("health"),
            rs.getInt("power"),
            rs.getInt("defense"),
            rs.getInt("exp"),
            rs.getInt("min_level"));

    private final JdbcClient db;

    public EnemyRepository(JdbcClient db) {
        this.db = db;
    }

    public EnemyType byName(String name) {
        return db.sql("SELECT " + COLUMNS + " FROM enemies WHERE name = :name")
                .param("name", name)
                .query(ROW)
                .optional()
                .orElseThrow(() -> new NoSuchElementException(
                        "No enemy named " + name + " (is db/seed.sql loaded?)"));
    }

    @Override
    public List<EnemyType> upToLevel(int level) {
        return db.sql("SELECT " + COLUMNS + " FROM enemies WHERE min_level <= :level"
                        + " ORDER BY enemy_id")
                .param("level", level)
                .query(ROW)
                .list();
    }

    public List<EnemyType> all() {
        return db.sql("SELECT " + COLUMNS + " FROM enemies ORDER BY enemy_id")
                .query(ROW)
                .list();
    }
}
