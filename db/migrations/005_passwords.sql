-- Passwords, for signing in. Only a bcrypt hash is stored, never the password itself.
--
-- Players made before this have no password and can't sign in until one is set with
-- db/set_password.sql.
--
-- Run manually against the target database, e.g.:
--   psql -h localhost -U postgres -d rogue_social -f db/migrations/005_passwords.sql

BEGIN;

ALTER TABLE public.users ADD COLUMN password_hash varchar(60);

COMMIT;
