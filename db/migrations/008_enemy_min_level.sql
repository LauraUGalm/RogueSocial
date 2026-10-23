-- Minimum level: the lowest player level an enemy can appear for, so a level 1 player never
-- meets a dragon.
--
-- Run manually against the target database, e.g.:
--   psql -h localhost -U postgres -d rogue_social -f db/migrations/008_enemy_min_level.sql

BEGIN;

-- The default fills in bat and scorpion, which are both level 1 enemies. It's dropped below so a
-- new enemy has to say what level it starts at, like its other stats.
ALTER TABLE public.enemies
    ADD COLUMN min_level integer NOT NULL DEFAULT 1
        CONSTRAINT enemies_min_level_min CHECK (min_level >= 1);

ALTER TABLE public.enemies ALTER COLUMN min_level DROP DEFAULT;

COMMIT;
