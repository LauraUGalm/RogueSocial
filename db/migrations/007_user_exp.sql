-- Experience: the exp a player has banked. Like gold, exp earned in a run is only banked when
-- the player leaves the dungeon alive. `level` follows from it (see Levels.java).
--
-- Run manually against the target database, e.g.:
--   psql -h localhost -U postgres -d rogue_social -f db/migrations/007_user_exp.sql

BEGIN;

ALTER TABLE public.users
    ADD COLUMN exp bigint NOT NULL DEFAULT 0 CONSTRAINT users_exp_nonneg CHECK (exp >= 0);

COMMIT;
