-- =============================================================================
-- English Lab - V20: verificación de correo electrónico
--  * users.email_verified / email_verified_at
--  * email_verification_tokens (solo se guarda el HASH SHA-256 del token)
-- =============================================================================

alter table users
    add column email_verified boolean not null default false,
    add column email_verified_at timestamptz;

-- Cuentas ya existentes (seed, admin/profesores y estudiantes creados antes de esta
-- funcionalidad) se consideran verificadas para no dejar a nadie fuera.
update users set email_verified = true, email_verified_at = created_at;

create table email_verification_tokens (
    id bigint generated always as identity primary key,
    user_id bigint not null references users (id) on delete cascade,
    token_hash varchar(64) not null,
    expires_at timestamptz not null,
    used_at timestamptz,
    created_at timestamptz not null default now()
);

create unique index ux_evt_token_hash on email_verification_tokens (token_hash);
create index ix_evt_user_created on email_verification_tokens (user_id, created_at desc);

-- Para la purga periódica de cuentas que nunca se verificaron.
create index ix_users_unverified_created on users (created_at) where email_verified = false;
