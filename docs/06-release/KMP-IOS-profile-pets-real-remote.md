# KMP-IOS — Profile + Pets REAL_REMOTE (Bloque 6)

## Runtime

```text
SharedRemoteRuntime (internal)
  SupabaseClient { Auth + Postgrest + SecureStorageSessionManager }
  → AuthRepository
  → UserProfileRepository (RemoteUserProfileRepository)
  → SharedPetsRepository (RemoteSharedPetsRepository)
```

Un solo cliente / sesión / Keychain. Sin auth paralelo.

## Profile

- Fuente: `users` SELECT propio (RLS `auth.uid() = id`)
- Mapper → `UserProfileSummary`
- Email: fila users ?: Auth session
- Nombre: display_name ?: name ?: email local-part
- Avatar: `avatar_path` / `profile_image_url` como ref (sin signed URL — PARCIAL)

## Pets

- Lista: RPC `m08_list_accessible_pets` (`p_status=ACTIVE`)
- Detalle: `pets` SELECT por id (RLS M08)
- UI SAFE: sin ownerId / principal / microchip / health
- `passportHint`: null (M14 no en path Android detail actual)
- Media: `hasAvatar` booleano — rendering URL PARCIAL

## Host iOS

`PocIosViewController` → `SharedRemoteRuntime.create(...)` + Fake LF/Adoption.
