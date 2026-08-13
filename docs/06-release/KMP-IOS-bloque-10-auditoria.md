# KMP-IOS — Bloque 10 auditoría (M05 MEDIA READ)

**HEAD base:** `290550324a4365f6b6e25ca21d055997d296a2a6` (KMP-9 CLOSED GREEN / Gate #13 PASS)

## Android productive READ (referencia)

| Pieza | Hallazgo |
| ----- | -------- |
| `FileAsset` / repos | `FileAssetRepository.getAsset` → RPC `get_file_asset` |
| Display | `FileDisplayResolver` + `SupabaseFileDownloadRepository.requestSignedUrl` |
| PUBLIC | `resolve_public_file_asset` → bucket/path → `createSignedUrl` |
| PRIVATE | `request_file_signed_url` + TTL class |
| TTL | SQL STANDARD **3600s** / SENSITIVE **600s**; client PUBLIC fallback **300s**, else **600s** |
| UI Android | **No llama** `fileDisplayResolver` en Lost/Found/Adoption (gap productivo) |
| Persistencia signed URL | **Prohibida** (`FileAssetRules.mustNotPersistSignedUrl`) |

## Vertical matrix (shared KMP-10)

| Vertical | REMOTE_FIELD | SEMANTICS | RESOLUTION | Status |
| -------- | ------------ | --------- | ---------- | ------ |
| Lost/Found | `photo_url` | **assetId UUID** (KMP-9) | `MediaRef.Asset` → signed temp → bytes | REAL_REMOTE |
| Adoption | `photo_url` | assetId o HTTPS legacy | mismo parser | REAL_REMOTE si campo resoluble |
| Pets | `avatar_file_asset_id` canónico; `photo_url` legacy | Asset / HTTPS | `fromPetFields` | REAL_REMOTE si asset/HTTPS |
| Profile | `avatar_path` / `profile_image_url` | path storage vs HTTPS | solo HTTPS → RemoteUrl; path solo → null | PARTIAL (path sin firmador shared) |

## Decisiones

- No Coil3: Ktor download de signed URL + `decodeImageBytes` expect/actual.
- Un solo `SupabaseClient` / `SharedRemoteRuntime.mediaResolver`.
- Cache memoria + TTL/skew + clear en logout.
- KT-86501 workaround simulator preservado.
