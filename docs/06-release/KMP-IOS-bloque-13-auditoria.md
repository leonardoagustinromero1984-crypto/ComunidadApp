# KMP-IOS — Bloque 13 auditoría (Profile edit + avatar)

Fuente: Android/backend comprometido (`UserSupabaseDataSource`, `ProfileAvatarStorageService`, migración 016/017).

## UPDATE CONTRACT

| Item | Valor |
| ---- | ----- |
| RPC | `update_my_profile` |
| Campos KMP | `p_display_name`, `p_bio`, `p_city`, `p_province`, `p_avatar_path` |
| No editables en KMP | email, roles, org, verification, privacy flags, username onboarding completo |

## EDITABLE FIELDS (safe subset)

- displayName
- city / province (zona aproximada)
- avatar_path (tras upload)

## AVATAR READ

- Persistido: `users.avatar_path` path privado bucket `profile-avatars`.
- Android: `ProfileAvatarStorageService.createSignedUrl(path, 3600)`.
- Shared: `MediaRef.ProfileAvatarPath` + `resolveProfileAvatarPath` (mismo bucket/TTL).
- Resultado: **PROFILE MEDIA READ = REAL_REMOTE** para paths `users/{uid}/avatar/...`.

## AVATAR WRITE

- Legacy path: `users/{uid}/avatar/{file}` en `profile-avatars` (no M05 `avatars/{assetId}` — RPC rechaza ese prefijo).
- Flujo: PHPicker → FileRef → upload Storage → `update_my_profile(p_avatar_path)` → invalidate cache → refresh.

## CACHE

- `MediaResolver.invalidateProfileAvatars()` tras update avatar.
- Logout sigue con `clearCache()`; no persistir signed URL.
