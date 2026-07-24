# M13 — Persistencia y seguridad (Bloque 2)

**LeoVer** · Estado: **BLOQUE 2 CERRADO LOCALMENTE** · Migración **048** creada · **no aplicada remotamente**.

## Estrategia

Tabla lateral **1:1** `lost_found_sighting_details` sobre `lost_found_sightings` (legacy `012`), sin drop/recreate destructivo.

Casos activos: `lost_found_posts` con `status = 'ACTIVE'`. Autoridad: `author_id` (dueño del caso) + `has_permission` / catálogo org `lostfound.*`.

## Tablas

| Tabla | Rol |
|-------|-----|
| `lost_found_sightings` | Legacy (sin DROP) |
| `lost_found_sighting_details` | Detalle M13 1:1 |
| `lost_found_match_candidates` | Candidatos score/nivel/razones |
| `lost_found_match_decisions` | Estructura Bloque 3 (sin RPC write) |
| `lost_found_match_status_history` | Historial de estados |

## RPC cliente (13)

`m13_create_sighting`, `m13_update_my_sighting`, `m13_withdraw_my_sighting`, `m13_get_sighting`, `m13_list_public_sightings`, `m13_list_my_sightings`, `m13_list_managed_sightings`, `m13_generate_match_candidates_for_sighting`, `m13_generate_match_candidates_for_case`, `m13_list_case_match_candidates`, `m13_list_sighting_match_candidates`, `m13_get_match_candidate`, `m13_recalculate_match_candidate`.

Sin `m13_confirm` / `m13_reject` (Bloque 3).

## Seguridad

- `SECURITY DEFINER` + `search_path = public`
- Actor desde `auth.uid()`
- RLS en tablas nuevas; DML cliente denegado (escritura vía RPC)
- Helpers `_m13_*` revocados a `public`/`anon`/`authenticated`
- Grants `EXECUTE` solo a `authenticated` en RPC cliente
- Media: prefijos `m05://` y `file_asset:`
- Sin `service_role` en Android; sin autoconfirmación

## Android

- `SupabaseM13RemoteDataSource` + `SupabaseM13SightingRepository` / `SupabaseM13MatchRepository`
- `DataProvider` conmuta mock ↔ Supabase según `useSupabase`
- Confirm/reject remoto: fallan deliberadamente (`MATCH_INVALID_TRANSITION` / `MATCH_AUTO_CONFIRM_FORBIDDEN`)

## Pendientes

- Aplicación remota de 048 + validación estructural
- Smoke remoto
- Bloque 3: confirmación/rechazo humano remoto
