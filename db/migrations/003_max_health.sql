-- Health. This is the player's maximum; the health they have right now belongs to a run, which
-- starts full. New players start with 20; existing rows get the same.
--
-- Run manually against the target database, e.g.:
--   psql -h localhost -U postgres -d rogue_social -f db/migrations/003_max_health.sql

BEGIN;

ALTER TABLE public.users
    ADD COLUMN max_health integer NOT NULL DEFAULT 20 CONSTRAINT users_max_health_min CHECK (max_health >= 1);

COMMIT;
