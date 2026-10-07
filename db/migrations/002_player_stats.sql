-- Player stats for combat: level, power (how hard they hit) and defense (how much they shrug
-- off). New players start at level 1 with 5 power and 5 defense; existing rows get the same.
--
-- Run manually against the target database, e.g.:
--   psql -h localhost -U postgres -d rogue_social -f db/migrations/002_player_stats.sql

BEGIN;

ALTER TABLE public.users
    ADD COLUMN level   integer NOT NULL DEFAULT 1 CONSTRAINT users_level_min   CHECK (level >= 1),
    ADD COLUMN power   integer NOT NULL DEFAULT 5 CONSTRAINT users_power_min   CHECK (power >= 0),
    ADD COLUMN defense integer NOT NULL DEFAULT 5 CONSTRAINT users_defense_min CHECK (defense >= 0);

COMMIT;
