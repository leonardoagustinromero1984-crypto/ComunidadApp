# KMP-IOS — Bloque 16 validación

## Implementación

| Ítem | Estado |
| ---- | ------ |
| Create | `m08_create_pet_with_principal` REAL_REMOTE |
| Campos | name (req), species, sex, size, description |
| Owner | sesión autenticada (sin org selector en form mínimo) |
| Status inicial | ACTIVE (backend) |
| Avatar | M05 `PET_AVATAR` + `m08_set_pet_avatar_asset` opcional |
| Partial media | Success de pet + PartialSuccess si falla foto |

## Tests

`PetCreateVerticalTest`

## Suite

Ver `KMP-IOS-paquete-14-16-validacion.md`.
