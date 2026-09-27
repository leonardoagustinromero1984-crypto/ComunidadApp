# SEC-03 — Controles de abuso y costo (STAGING)

Proyecto: `tobqbddfcyitwgbkthhy`. Defaults de STAGING, no política comercial de PRODUCCIÓN.

Android no es autoridad. Los límites viven en `security_rate_limit_policies` y se consumen con upsert atómico en `security_usage_windows`.

## Superficies

| Superficie | Tipo de costo | Límite actual | Protección | Riesgo residual |
|---|---|---|---|---|
| Login / `admin_begin_login` | Auth + DB | throttle username (1061/1062) | hash, no IP cruda | bajo |
| Posts / reels / stories | DB + Storage | ver tabla de policies | RPC + revoke INSERT | medio (video) |
| Comments / reactions | DB | 30 / 60 por 10 min | wrapper RPC | bajo |
| Connections | DB | 20/h request y cancel | wrapper RPC | bajo |
| Messages / conversaciones | DB | 40/min; 15 conversaciones/h | wrapper RPC | bajo |
| Reports | DB + cola | 15/h | RPC; INSERT revocado | bajo |
| Media register / bytes / video | Storage + DB | 30/h; 200 MiB/día; 8 videos/día | fail closed | medio |
| Signed URL (RPC authorize) | ancho de banda | 60 / 10 min | ownership + consume | Storage SDK directo |
| VitaCora import | Edge + CPU + Storage | 5 MB / 500 filas; 6 analyze / 4 execute por hora | flag + consume | XLSX hostil |
| Search / feed | lectura | search persons 20; PostgREST `max_rows` 1000 | caps parciales | feed/messages jsonb sin cursor |
| Admin staff / roles | Auth + Edge | 10/10 min; 20 roles/h | AAL2 + consume | bajo |
| Push | notificaciones | no desplegado | no deploy | n/a |
| M27 rate limit | OAuth apps | legado | no reutilizar para producto | n/a |

## Fail closed

| Categoría | Si falla el contador |
|---|---|
| Social no crítico (post, comment, reaction, chat, report, connection) | fail open — la app no queda inutilizable |
| Media, signed URL, imports, admin | fail closed → `RATE_LIMITED` |

## Kill switches (sin APK nueva)

`security_feature_flags`:

- `media.video.upload.enabled`
- `reels.create.enabled`
- `imports.enabled`

SUPERADMIN futuro puede cambiarlas por SQL. No hay UI en SEC-03.

## Signed URL

`canon_authorize_media_signed_url` comprueba `_acl_media_readable` y consume cuota. Devuelve bucket/path/TTL, no token. Android llama esta RPC antes de `createSignedUrl`.

Residual P1: la Storage API de Supabase puede mintar un signed URL si el JWT pasa RLS, sin pasar por la RPC. Cerrar eso exige Edge/service_role dedicada (no se despliega en SEC-03).

## Presupuesto externo

No se modificó billing. Capacidades reales de Supabase (sin inventar):

- Alertas de uso / spend: dashboard del plan; no se configuraron aquí.
- Hard cap de gasto: no expuesto por SQL/CLI en este entorno.
- PITR / retención: ver SEC-04; requiere dashboard.
- LeoVer puede cortar: flags, policies, no desplegar push, no mint masivo.

## Eventos sanitizados

`RATE_LIMIT_EXCEEDED`, `UPLOAD_QUOTA_EXCEEDED`, `SIGNED_URL_THROTTLED`, `IMPORT_THROTTLED`. Metadata: `operation_key`, `used_units`. Sin bodies, tokens, URLs, salud, PII.
