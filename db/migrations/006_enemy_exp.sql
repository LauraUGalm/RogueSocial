-- Experience: how much exp a player earns for killing each kind of monster.
--
-- Run manually against the target database, e.g.:
--   psql -h localhost -U postgres -d rogue_social -f db/migrations/006_enemy_exp.sql

BEGIN;

-- The default only fills in the rows that already exist; it's dropped below so a new enemy has to
-- say what it's worth, like its other stats.
ALTER TABLE public.enemies
    ADD COLUMN exp integer NOT NULL DEFAULT 0 CONSTRAINT enemies_exp_min CHECK (exp >= 0);

UPDATE public.enemies SET exp = 1 WHERE name = 'bat';
UPDATE public.enemies SET exp = 3 WHERE name = 'scorpion';

ALTER TABLE public.enemies ALTER COLUMN exp DROP DEFAULT;

COMMIT;
