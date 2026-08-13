# KMP-IOS — Bloque 8 auditoría (Lost/Found WRITE)

**HEAD base esperado:** `a65d05b088330da59e5e1a390ca367b1ee4fb4f3`

**KMP-7:** CLOSED GREEN — GitHub Actions KMP iOS Validation #9 PASS (macOS real)

## Write Android productivo (exacto)

| Clave | Valor |
| ----- | ----- |
| CREATE_REMOTE_SOURCE | PostgREST **insert** table `lost_found_posts` (`LostFoundSupabaseDataSource.addLostFound`) — **no RPC** |
| UPDATE_REMOTE_SOURCE | PostgREST **update** same table (photo_url = M05 `assetId` after upload) |
| INITIAL_STATUS | `ACTIVE` (model default + DB default) |
| AUTHOR_MAPPING | `author_id` / `author_name` from authenticated user (`resolveAuthor`); RLS `auth.uid() = author_id` |
| LOCATION_FIELDS | `location` text **required**; `latitude`/`longitude` optional — **never set on Android publish** |
| CONTACT_FIELDS | `contact_info` text **required** (DB NOT NULL); free-text form; no profile auto-fill |
| PHOTO_UPLOAD_FLOW | insert → M05 `FileUploadCoordinator` (`LOST_FOUND_MEDIA` / `LOST_FOUND_CASE`) → update `photo_url=assetId` |
| PHOTO_STORAGE_BUCKET | logical `PUBLIC_MEDIA` → physical `public-media` |
| PHOTO_PATH_RULE | `lost_found/{caseId}/{assetId}/{safeFilename}` via M05 session RPCs |
| RETURNING_ROW | **No** — client UUID returned only |
| PUBLIC_CODE_GENERATION | DB trigger `_web_ensure_public_code` on INSERT (client omits) |
| RLS_REQUIREMENTS | insert/update own (`auth.uid() = author_id`); select authenticated |

## Implicaciones KMP-8

1. **Texto REAL_REMOTE** viable con insert + sesión (misma tabla KMP-7).
2. **Coords:** no requeridas; UI solo `ApproximateLocation` → `location` text.
3. **contact_info:** obligatorio en DB. KMP no pide teléfono/email: deriva nota segura desde sesión / nota corta opcional; **nunca** se expone en modelos SAFE de lectura.
4. **Media M05:** depende de RPCs `create_file_upload_session` + coordinator Android. No portado a `:shared` en este bloque → **MEDIA WRITE = PARTIAL** (publicación sin foto funcional; FileRef opcional no simula upload exitoso).
5. **Adoption write / APNs / FCM:** fuera de alcance.

## WIP preservado

M09 / `SupabaseRowDecoding` / M29 — no tocar.
