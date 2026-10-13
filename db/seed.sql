-- Starting data for a new database. Safe to run more than once.

-- The one player while there is no sign-in. The server plays as this user (rogue.player in
-- application.properties).
--   psql -h localhost -U postgres -d rogue_social -f db/seed.sql

INSERT INTO public.users (username, email)
VALUES ('laura', 'laura@localhost')
ON CONFLICT DO NOTHING;

-- The monsters.
INSERT INTO public.enemies (name, health, power, defense) VALUES
    ('bat',       3, 2, 1),
    ('scorpion', 10, 7, 3)
ON CONFLICT (name) DO NOTHING;
