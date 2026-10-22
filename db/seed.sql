-- Starting data for a new database. Safe to run more than once.

-- The first player. It has no password: give it one with db/set_password.sql to sign in.
--   psql -h localhost -U postgres -d rogue_social -f db/seed.sql

INSERT INTO public.users (username, email)
VALUES ('laura', 'laura@localhost')
ON CONFLICT DO NOTHING;

-- The monsters.
INSERT INTO public.enemies (name, health, power, defense, exp) VALUES
    ('bat',       3, 2, 1, 1),
    ('scorpion', 10, 7, 3, 3)
ON CONFLICT (name) DO NOTHING;
