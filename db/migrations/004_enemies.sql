-- Enemies: one row per kind of monster, with its stats. Floors read this table when they are
-- built, so a change here applies to the next new floor. `name` is also the sprite's name in
-- sprites.json.
--
-- Run manually against the target database, e.g.:
--   psql -h localhost -U postgres -d rogue_social -f db/migrations/004_enemies.sql

BEGIN;

CREATE TABLE public.enemies (
    enemy_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name     varchar(32) NOT NULL
        CONSTRAINT enemies_name_unique UNIQUE
        CONSTRAINT enemies_name_format CHECK (name ~ '^[a-z_]+$'),
    health   integer NOT NULL CONSTRAINT enemies_health_min  CHECK (health >= 1),
    power    integer NOT NULL CONSTRAINT enemies_power_min   CHECK (power >= 0),
    defense  integer NOT NULL CONSTRAINT enemies_defense_min CHECK (defense >= 0)
);

INSERT INTO public.enemies (name, health, power, defense) VALUES
    ('bat',       3, 2, 1),
    ('scorpion', 10, 7, 3);

COMMIT;
