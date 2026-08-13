# KMP-IOS — Bloque 9 auditoría (M05 media shared)

**HEAD base esperado:** `9072c86d4ddebbd56c9126d75fc31a7d7fb9b4a1`

**KMP-8:** CLOSED GREEN — GitHub Actions KMP iOS Validation #10 PASS

## Contratos M05 exactos (Android productivo)

| Clave | Valor |
| ----- | ----- |
| CREATE_SESSION_CONTRACT | RPC `create_file_upload_session` → jsonb `{session,asset,version}` |
| UPLOAD_CONTRACT | `storage.from(physicalBucket).upload(path, bytes)` — **no** signed upload URL |
| COMPLETE_SESSION_CONTRACT | RPC `complete_file_upload` (`p_session_id`) |
| ABORT_CONTRACT | RPC `cancel_file_upload` / `fail_file_upload` |
| Transition | RPC `transition_file_upload_session` (`p_state=UPLOADING`) |
| Progress | RPC `update_file_upload_progress` |
| BUCKET | logical `PUBLIC_MEDIA` → physical `public-media` (deny `leover`) |
| PATH | `lost_found/{caseId}/{assetId}/{safeFilename}` (server builds; client sends `p_storage_path=null`) |
| PURPOSE | `LOST_FOUND_MEDIA` |
| ENTITY_TYPE | `LOST_FOUND_CASE` |
| OWNER | `USER` + `auth` user id; visibility `PUBLIC` |
| MAX_BYTES | `8388608` |
| ALLOWED_MIME | `image/jpeg`, `image/png`, `image/webp` |
| RETURNED_ASSET | session.asset_id (+ version.storage_bucket/path) |
| PHOTO_URL_SEMANTICS | `lost_found_posts.photo_url` = **assetId UUID** (no HTTPS URL) |
| RLS | RPCs execute → `authenticated`; storage INSERT path tied to session |
| AUTH | misma sesión Supabase |

## Secuencia Android Lost/Found

1. insert `lost_found_posts` (`photo_url` null)
2. create session → transition UPLOADING → storage upload → complete
3. update `photo_url = assetId`
4. Si media falla: **no borra** el post (orphan sin foto)

## KMP-9

Portar solo este slice a `:shared` con un único `SupabaseClient` (Auth+Postgrest+Storage).

MEDIA READ rendering: **PARTIAL** (assetId en modelo; signed display URL deferred).
