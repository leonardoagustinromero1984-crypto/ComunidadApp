# KMP-IOS — Bloque 11 validación

## Implementación

| Ítem | Estado |
| ---- | ------ |
| RPC create | `m09_create_adoption_publication` |
| Form CMP | pet + title + description + requirements + zona |
| Actor | sesión autenticada |
| Media write | **PARTIAL** (foto = pet snapshot; sin M05 write aparte) |
| Refresh | sí tras publish |
| Fake fallback | NO |

## Tests focalizados

`AdoptionPublishVerticalTest` — draft válido/invalid, auth, forbidden, success, conflict, locality, refresh, unconfigured.

## Suite

Ver `KMP-IOS-paquete-11-13-validacion.md`.
