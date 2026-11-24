-- =============================================================================
-- English Lab - V21: identidades de proveedores externos (Google)
--  * user_identities guarda el vínculo (provider, sub) -> usuario.
--    Se identifica por el `sub` inmutable de Google, NUNCA por el correo (que puede cambiar).
--  * Un usuario solo puede tener una identidad por proveedor, y un `sub` solo pertenece a un usuario.
--  * Las credenciales (password_hash) siguen en users: el registro tradicional no se toca.
-- =============================================================================

create table user_identities (
    id bigint generated always as identity primary key,
    user_id bigint not null references users (id) on delete cascade,
    provider varchar(20) not null,
    provider_subject varchar(255) not null,
    email varchar(255) not null,
    created_at timestamptz not null default now(),
    last_login_at timestamptz,
    constraint ck_user_identities_provider check (provider in ('GOOGLE'))
);

create unique index ux_user_identities_provider_subject on user_identities (provider, provider_subject);
create unique index ux_user_identities_user_provider on user_identities (user_id, provider);
