-- The one player while there is no sign-in. The server plays as this user (rogue.player in
-- application.properties). Safe to run more than once.
--   psql -h localhost -U postgres -d rogue_social -f db/seed.sql

INSERT INTO public.users (username, email)
VALUES ('laura', 'laura@localhost')
ON CONFLICT DO NOTHING;
