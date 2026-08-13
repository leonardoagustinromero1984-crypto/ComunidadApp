# KMP-IOS — Bloque 13 validación

## Implementación

| Ítem | Estado |
| ---- | ------ |
| Update | `update_my_profile` REAL_REMOTE |
| Campos | displayName, city, province, avatar_path |
| Avatar read | `users/.../avatar/...` → signed → bytes REAL_REMOTE |
| Avatar write | bucket `profile-avatars` legacy REAL_REMOTE |
| Cache invalidate | sí |
| Fake fallback | NO |

## Tests

`ProfileUpdateVerticalTest` + regresión media read profile paths.

## Suite

Ver `KMP-IOS-paquete-11-13-validacion.md`.
