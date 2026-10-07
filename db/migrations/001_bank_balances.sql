-- The bank (README section 3): gold and silver kept safe outside the dungeon. Gold is credited
-- when a player leaves the dungeon alive. Items will need their own table later.
--
-- Run manually against the target database, e.g.:
--   psql -h localhost -U postgres -d rogue_social -f db/migrations/001_bank_balances.sql

BEGIN;

ALTER TABLE public.users
    ADD COLUMN bank_gold   bigint NOT NULL DEFAULT 0 CONSTRAINT users_bank_gold_nonneg CHECK (bank_gold >= 0),
    ADD COLUMN bank_silver bigint NOT NULL DEFAULT 0 CONSTRAINT users_bank_silver_nonneg CHECK (bank_silver >= 0);

COMMIT;
