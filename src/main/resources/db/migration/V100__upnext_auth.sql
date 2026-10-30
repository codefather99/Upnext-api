-- =====================================================================
-- V100__upnext_auth.sql          OWNER: Henry (API, auth and pipeline)
-- =====================================================================
CREATE TABLE app_user (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         varchar(160) NOT NULL,
    password_hash varchar(100) NOT NULL,                 -- bcrypt, never the password
    display_name  varchar(80)  NOT NULL,
    role          varchar(10)  NOT NULL DEFAULT 'USER' CHECK (role IN ('USER','ADMIN')),
    created_at    timestamptz  NOT NULL DEFAULT now()
);
-- Emails are unique ignoring case. Two simultaneous signups with one email: one wins, one gets EMAIL_TAKEN.
CREATE UNIQUE INDEX ux_app_user_email ON app_user (lower(email));
