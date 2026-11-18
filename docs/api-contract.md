# Contrato de API - English Lab

Estado: **Inc 0** (solo el health endpoint esta implementado). Todo lo demas en este documento es el baseline acordado para Inc 1-3; cambiar este contrato obliga a avisar a los 3 integrantes.

## Base URL

```
/api/v1
```

Produccion: `https://<dominio>/api/v1` (Nginx termina en HTTPS y reenvia al backend en el puerto 8080).

---

## Implementado en Inc 0

| Metodo | Ruta | Auth | Respuesta |
|--------|------|------|-----------|
| GET | `/api/v1/health` | ninguna (publico) | `200 {"status":"ok"}` |

- Cualquier fallo no previsto responde `500` generico, sin stacktrace al cliente (se loguea en el servidor).
- No hay mas endpoints en este incremento. Auth, intentos, recomendaciones y asignaciones llegan en Inc 1+.

---

## Convenciones del contrato (baseline Inc 1-3)

**Prefijo.** Todas las rutas van bajo `/api/v1/...`. Nada fuera de ese prefijo.

**Autenticacion.** JWT en Bearer:

```
Authorization: Bearer <jwt>
```

Claims minimos:

- `sub`: username o email del usuario.
- `rol` (o `roles`): uno de `ADMIN`, `TEACHER`, `STUDENT`.
- `exp`: expiracion.

Sin token o con token invalido/revocado → `401`. Con token valido pero sin permiso → `403`.

**Errores.** Uniformes, con `ProblemDetail` via `@RestControllerAdvice`:

- `400` cuerpo mal formado o validacion de campos (con `fieldErrors`).
- `401` sin autenticacion o token revocado (logout invalida en `revoked_tokens`).
- `403` rol insuficiente (la cadena de seguridad lo genera: `AuthenticationEntryPoint` / `AccessDeniedHandler`).
- `404` recurso inexistente o sin pertenencia al usuario.
- `422` regla de negocio violada (p. ej. alumno ya vinculado, actividad no asignada).
- `500` generico, sin detalles al cliente.

**Paginacion y filtros.** Siempre en servidor: parametros `page`, `size` (tope 100) y `sort` con lista blanca de campos; respuesta `{content, page, size, totalElements, totalPages}`. Los filtros (rol, texto, nivel, habilidad, tipo) via parametros opcionales aplicados en la consulta, jamas filtrando en memoria.

**DTOs.** Entrada y salida siempre con DTOs (records). Nunca se exponen entidades JPA, hashes de contrasena ni la clave de respuesta de una actividad antes de evaluar.

---

## Plan de migraciones Flyway

`backend/src/main/resources/db/migration`. Se crean todas en **Inc 1** (hoy no existe ninguna V*).

| Version | Contenido | Tablas | Dueño |
|---------|-----------|--------|-------|
| V1 | Auth | `roles`, `users`, `password_reset_tokens`, `revoked_tokens` | A |
| V2 | Contenido | `objectives`, `objective_prerequisites`, `activities` | B |
| V3 | Aprendizaje | `attempts`, `learner_skill_state`, `assignments`, `notifications_log` | C |
| V10 | Seed de objetivos | datos semilla | A |
| V11 | Seed de actividades | datos semilla | B |
| V12 | Seed de usuarios/pruebas | datos semilla | C |

Notas:

- `ddl-auto=validate`: el esquema lo manda unicamente Flyway.
- `revoked_tokens` es la unica mecanica de logout (sin `token_version`).
- Cada integrante solo toca la version que le corresponde; si necesitas una columna nueva, se agrega una version V13+ en vez de editar una migracion ya aplicada.

---

## Roles

| Rol | Alcance |
|-----|---------|
| `ADMIN` | Gestion global: usuarios, configuracion, ver todo. |
| `TEACHER` | Ve y gestiona solo a sus alumnos vinculados; crea asignaciones. |
| `STUDENT` | Ve solo sus propios datos, intentos y recomendaciones. |

---

## Resumen: implementado vs planificado

- **Implementado (Inc 0):** `GET /api/v1/health`.
- **Planificado (Inc 1+):** auth completa (login, registro, logout, recuperacion de clave), objetivos/actividades, intentos y calificacion, recomendaciones del nucleo adaptativo, asignaciones, notificaciones, seeds y toda la coleccion Bruno con evidencias de errores.
