# Migraciones Flyway

Las tablas se crean **solo** con Flyway (`spring.jpa.hibernate.ddl-auto=validate`).
Nunca editar una migracion ya fusionada a `main`: crear una nueva version.

## Reparto de versiones (evita choques)

| Version      | Dueno/a | Tablas                                                                 |
|--------------|---------|------------------------------------------------------------------------|
| V1__schema_auth.sql     | A       | roles, users, password_reset_tokens, revoked_tokens                    |
| V2__schema_content.sql  | B       | objectives, objective_prerequisites (N:M), activities (content jsonb)  |
| V3__schema_learning.sql | C       | attempts, learner_skill_state, assignments, notifications_log          |
| V10 / V11 / V12         | A/B/C   | seeds de cada modulo                                                    |

Los seeds usan V10+ para no intercalarse con el esquema.
