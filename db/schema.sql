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
    created_at timestamptz  NOT NULL DEFAULT now()
);

-- A username or email is taken regardless of case ("Laura" == "laura"). The original
-- spelling is kept for display.
CREATE UNIQUE INDEX users_username_lower_uidx ON public.users (lower(username));
CREATE UNIQUE INDEX users_email_lower_uidx    ON public.users (lower(email));

COMMIT;
