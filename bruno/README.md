# Coleccion Bruno de English Lab

Esta carpeta guarda la coleccion de peticiones de la API, hechas con [Bruno](https://usebruno.com/) (no Postman).

**Estado:** aun sin peticiones. En **Inc 1+** se añadiran las carpetas `auth`, `users`, `activities`, `attempts`, `recommendations`, `assignments` y `errors`, con un entorno que define `baseUrl` (`/api/v1`) y guarda el JWT en `token` tras el login.

Cada entrega incluira evidencias de errores intencionales: `403` (estudiante en ruta admin), `400`/`422` (datos invalidos y reglas de negocio), `404` y `401` (token revocado tras logout). Ver skill `bruno-readme-evidence`.
