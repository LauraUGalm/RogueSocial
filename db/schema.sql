-- Rogue Social: the current shape of the database.
--
-- For a new, empty database. Changes to an existing database go in db/migrations/ as numbered
-- scripts, and this file is updated to match, so it always shows the whole current shape.
--
-- Create the database and load this file, e.g.:
--   createdb -h localhost -U postgres rogue_social
--   psql -h localhost -U postgres -d rogue_social -f db/schema.sql

BEGIN;

-- One row per player. Sign-in is not designed yet (README section 11), so there is no
-- password or login data here.
CREATE TABLE public.users (
    user_id    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Shown on posts. Letters, digits and underscores, 3 to 32 of them.
    username   varchar(32)  NOT NULL
        CONSTRAINT users_username_format CHECK (username ~ '^[A-Za-z0-9_]{3,32}$'),
    email      varchar(254) NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    -- The bank (README section 3). Gold is credited when the player leaves the dungeon alive.
    bank_gold   bigint NOT NULL DEFAULT 0 CONSTRAINT users_bank_gold_nonneg CHECK (bank_gold >= 0),
    bank_silver bigint NOT NULL DEFAULT 0 CONSTRAINT users_bank_silver_nonneg CHECK (bank_silver >= 0),
    -- Combat stats. Everyone starts at level 1 with 5 power, 5 defense and 20 health.
    level   integer NOT NULL DEFAULT 1 CONSTRAINT users_level_min   CHECK (level >= 1),
    power   integer NOT NULL DEFAULT 5 CONSTRAINT users_power_min   CHECK (power >= 0),
    defense integer NOT NULL DEFAULT 5 CONSTRAINT users_defense_min CHECK (defense >= 0),
    -- The most health the player can have. A run starts with this much.
    max_health integer NOT NULL DEFAULT 20 CONSTRAINT users_max_health_min CHECK (max_health >= 1)
);

-- A username or email is taken regardless of case ("Laura" == "laura"). The original
-- spelling is kept for display.
CREATE UNIQUE INDEX users_username_lower_uidx ON public.users (lower(username));
CREATE UNIQUE INDEX users_email_lower_uidx    ON public.users (lower(email));

COMMIT;
