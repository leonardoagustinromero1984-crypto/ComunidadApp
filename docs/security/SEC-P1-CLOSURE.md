# SEC-P1-CLOSURE — deuda técnica de seguridad (STAGING)

Fecha: 2026-09-04  
Proyecto: `tobqbddfcyitwgbkthhy`  
Migraciones: `20260904180000_1066_sec_p1_closure.sql`, `20260904220000_1067_sec_p1_signed_url_window.sql`  
Cursor **no** marca ACCEPTED RISK.

## Inventario signed URL (callers reales)

| Caller | Mecanismo | Clasificación | Acción |
|---|---|---|---|
| `CanonicalFileDownloadRepository.requestSignedUrl` | Edge `media-signed-url` → RPC authorize + service_role mint | PRIVATE MEDIA / DOCUMENT | camino canónico |
| Mismo caller, bucket `public-media` | URL pública | PUBLIC MEDIA | sin cambio |
| `ProfileAvatarStorageService.createSignedUrl` | SDK | LEGACY / UNUSED | bucket `profile-avatars` no existe en STAGING |
| `OrganizationMediaStorageService` / `OrganizationViewModels` | SDK | LEGACY / UNUSED | bucket `organization-media` no existe en STAGING |
| `M05MediaReadGateway` | SDK | LEGACY | no es runtime canónico |
| `SupabaseFileDownloadRepository` | RPC legado + SDK | LEGACY | RPC no está en canónico |
| `ProfileAvatarResolver` | via fileDownload / avatar service | residual | usa el camino canónico cuando DataProvider canónico |

## Clasificación

| Ítem | Estado | Notas |
|---|---|---|
| Signed URL SDK bypass / throttle | FIXED | SELECT privado removido; mint solo Edge + `canon_authorize_media_signed_url` (`signed_url.request`, fail-closed 1063) |
| `canon_list_social_feed` cursor | FIXED | límite 30 (máx. 50); cursor `(created_at, id)` |
| `canon_list_messages` cursor | FIXED | recientes 50; cursor `(created_at, id)` hacia atrás |
| MIME bucket-level | FIXED (4 buckets) | allowlist = `canon_register_media` |
| `moderation-evidence` MIME | NOT APPLICABLE TO CURRENT UPLOAD SURFACE / FUTURE DEPLOYMENT REQUIREMENT | sin INSERT cliente; `allowed_mime_types` null a propósito; no es ACCEPTED RISK |

## Contratos

**Signed URL privada:** Bearer válido → `getUser` → `canon_authorize_media_signed_url` (ACL + rate limit) → TTL ≤ 600 s → `createSignedUrl` con service_role solo server-side. No log de JWT ni URL.

**Feed:** 0-arg = primera página (30). 3-arg = `p_limit`, `p_cursor_created_at`, `p_cursor_id`. Visibilidad 1053 intacta. `composition` / `extra_media` intactos.

**Messages:** 1-arg = página reciente (50, payload ASC). 4-arg = página anterior. Participante required. Historial no se borra.

**MIME:** `public-media` / `private-media` = jpeg, png, webp, heic, heif, gif, mp4, quicktime, webm, 3gpp. `documents` / `vitacora-import` = pdf, xlsx, xls, csv, octet-stream.

**`moderation-evidence`:** CURRENT CLIENT UPLOAD SURFACE = N/A. CURRENT SECURITY EXPOSURE = sin INSERT cliente (`canon_storage_insert` no incluye el bucket). `allowed_mime_types` permanece null. No inventar allowlist sin contrato de evidencias.

BEFORE ANY moderation-evidence uploader is enabled:
- definir MIME necesarios
- aplicar bucket allowlist
- tamaño máximo
- autorización
- ownership / case scope
- malware/content handling si corresponde
- validar server-side además del bucket

## Evidencia dirigida (STAGING)

`tests/1066_sec_p1_closure.sql`: feed page1/page2/overlap/end PASS; messages recent/older/overlap PASS; MIME allow/deny PASS; owner authorize OK; stranger FORBIDDEN; throttle RATE_LIMITED; policy restaurada.  
`signed_url.request` 1067: `limit_count=60`, `window_seconds=600` (60 / 10 min), `fail_closed=true`. Restaura el residual 3600 de una prueba 1063.  
Aislamiento stranger de mensajes: sin tercera PERSON en fixtures; peer isolation OK; stranger cubierto por SEC-05 previo.

## No tocado

Health, Reel video vacío, Transferencias, Guardados, Responsables, Quitar responsable, Support, UX Catálogos, login/session/MFA. SEC-04 no se reabrió.
