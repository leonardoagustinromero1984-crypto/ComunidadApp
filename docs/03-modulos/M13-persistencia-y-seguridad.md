# M13 — Persistencia y seguridad (Bloque 2)

**LeoVer** · Estado: **BLOQUE 2 CERRADO LOCALMENTE** · Migración **048** aplicada en pruebas (estructural **13/13 PASS**) · smoke funcional B2 **PENDIENTE EXTERNO**.

## Estrategia

Tabla lateral **1:1** `lost_found_sighting_details` sobre `lost_found_sightings` (legacy `012`), sin drop/recreate destructivo.

Casos activos: `lost_found_posts` con `status = 'ACTIVE'`. Autoridad: `author_id` (dueño del caso) + `has_permission` / catálogo org `lostfound.*`.

## Tablas

| Tabla | Rol |
|-------|-----|
| `lost_found_sightings` | Legacy (sin DROP) |
| `lost_found_sighting_details` | Detalle M13 1:1 |
| `lost_found_match_candidates` | Candidatos score/nivel/razones |
| `lost_found_match_decisions` | Estructura (escritura remota → 049) |
| `lost_found_match_status_history` | Historial (escritura remota → 049) |

## RPC cliente (13 en 048)

`m13_create_sighting` … `m13_recalculate_match_candidate`.

Sin `m13_confirm` / `m13_reject` / `m13_open_match_review` (propuesta **049**).

## Seguridad

- `SECURITY DEFINER` + `search_path = public`
- Actor desde `auth.uid()`
- RLS en tablas nuevas; DML cliente denegado (escritura vía RPC)
- Helpers `_m13_*` revocados
- Media: `m05://` y `file_asset:`
- Sin autoconfirmación

## Android

- `SupabaseM13*` para sighting/generate/list (048)
- Review remoto: `MATCH_REVIEW_RPC_UNAVAILABLE` hasta 049
- Mock: flujo humano completo (Bloque 3 local)

## Pendientes

- Smoke funcional B2 externo
- Aprobación y creación de 049 para RPC de revisión
- Smoke remoto de revisión humana