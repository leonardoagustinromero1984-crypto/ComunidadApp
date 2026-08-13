# KMP-IOS — Bloque 6 auditoría (Profile + Pets REAL_REMOTE)

**HEAD base:** `005def3`
**WIP M09/decoding/M29:** preservado

## Profile

| Ítem | Valor |
| ---- | ----- |
| REMOTE_SOURCE | Tabla `users` SELECT `eq(id)` |
| CONTRACT | Mismo que `UserSupabaseDataSource.getUser` |
| AUTH_REQUIREMENT | RLS `auth.uid() = id` (solo propio) |
| DTO (shared mínimo) | `RemoteUserProfileRow` |
| MAPPER | → `UserProfileSummary` |
| SAFE_FIELDS | displayName, email propio, approximateLocation, avatarRef(path), timestamps |
| Precedencia nombre | `display_name` ?: `name` ?: email local-part |
| Precedencia email | fila `users.email` ?: Auth session email |
| ANDROID_ONLY | Signed avatar URL (`ProfileAvatarStorageService`) |
| READY_TO_SHARE | Lectura propia sí |

## Pets

| Ítem | Valor |
| ---- | ----- |
| REMOTE_SOURCE lista | RPC `m08_list_accessible_pets` (`p_status=ACTIVE`) |
| REMOTE_SOURCE detalle | Tabla `pets` SELECT por `id` (RLS `m08_actor_can_read_pet`) |
| AUTH_REQUIREMENT | JWT sesión; autorización en backend (no `owner_id == userId` client-side) |
| DTO | `RemoteAccessiblePetRow` / `RemotePetRow` (campos mínimos + ignoreUnknownKeys) |
| MAPPER | → `PetSummary` / `PetDetailView` (sin ownerId/principal/microchip) |
| SAFE_FIELDS | name, species, breed, sex, status, hasAvatar |
| Passport | Android detail no carga M14 → `passportHint = null` (brecha documentada) |
| Media | `hasAvatar` desde photo_url / avatar_file_asset_id; URL firmada PARCIAL |
| ANDROID_ONLY | `FileDisplayResolver`, Coil, M14 passport UI |
| READY_TO_SHARE | Lectura lista/detalle sí |

## Decisión runtime

Un solo `SharedRemoteRuntime` (internal) con `SupabaseClient` Auth+Postgrest+Keychain SessionManager.
Produce Auth / Profile / Pets repos. No export ObjC.
