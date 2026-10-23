-- More monsters: rats alongside bats and scorpions, spiders and snakes from level 2, wolves and
-- skeletons from level 4, and ghosts from level 5.
--
-- Run manually against the target database, e.g.:
--   psql -h localhost -U postgres -d rogue_social -f db/migrations/009_new_enemies.sql

BEGIN;

INSERT INTO public.enemies (name, health, power, defense, exp, min_level) VALUES
    ('rat',       5,  4, 2, 2, 1),
    ('spider',   13,  8, 4, 5, 2),
    ('snake',    12,  7, 4, 4, 2),
    ('wolf',     15, 10, 5, 8, 4),
    ('skeleton', 18,  9, 6, 8, 4),
    ('ghost',    10, 11, 7, 10, 5)
ON CONFLICT (name) DO NOTHING;

COMMIT;
