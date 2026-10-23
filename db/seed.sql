-- Starting data for a new database. Safe to run more than once.

-- The first player. It has no password: give it one with db/set_password.sql to sign in.
--   psql -h localhost -U postgres -d rogue_social -f db/seed.sql

INSERT INTO public.users (username, email)
VALUES ('laura', 'laura@localhost')
ON CONFLICT DO NOTHING;

-- The monsters.
INSERT INTO public.enemies (name, health, power, defense, exp, min_level) VALUES
    ('bat',       3,  2, 1, 1, 1),
    ('scorpion', 10,  7, 3, 3, 1),
    ('rat',       5,  4, 2, 2, 1),
    ('spider',   13,  8, 4, 5, 2),
    ('snake',    12,  7, 4, 4, 2),
    ('wolf',     15, 10, 5, 8, 4),
    ('skeleton', 18,  9, 6, 8, 4),
    ('ghost',    10, 11, 7, 10, 5)
ON CONFLICT (name) DO NOTHING;
