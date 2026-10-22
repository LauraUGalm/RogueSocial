-- Sets a player's password from the command line, for players made before sign-in (like laura
-- from seed.sql) or anyone who has forgotten theirs. It asks for the password, so it never lands
-- in your shell history:
--   psql -h localhost -U postgres -d rogue_social -v username=laura -f db/set_password.sql
--
-- The hash is bcrypt from Postgres's pgcrypto extension, the same kind the server makes. This
-- does not check the password rules the sign-up form does.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

\prompt 'New password: ' password

UPDATE public.users
SET password_hash = crypt(:'password', gen_salt('bf', 12))
WHERE lower(username) = lower(:'username');
