# M08 Etapa 4B — Repositorios y adaptador legacy

**Producto:** LeoVer  
**Rama:** `m08/etapa-4b-repositorios-adaptador-legacy`  
**Alcance:** Android data layer (RPC 035/036 + SELECT RLS). Sin migraciones nuevas.

```text
M08 ETAPA 4B — REPOSITORIOS Y ADAPTADOR LEGACY LISTOS LOCALMENTE
STAGING NO AUTORIZADO
REQUIERE ETAPA 4C — INTEGRACIÓN LOCAL Y SMOKE APK
```

## Objetivo

Adaptar el contrato legacy `PetRepository` a RPCs M08 sin INSERT/UPDATE/DELETE directo sobre `pets`, con `ownerId` nullable y ACL por `m08_get_pet_access_context`.

## Componentes

| Pieza | Ubicación |
|---|---|
| DTOs / mappers / errores | `data/remote/supabase/m08/` |
| Remote DS | `PetM08RemoteDataSource` + `SupabasePetM08RemoteDataSource` |
| Adaptador | `LegacyPetRepositoryAdapter` |
| Domain repos | `SupabasePet*Repository` |
| Wiring | `DataProvider.petRepository` → adapter si `useSupabase` |

## Reglas

- `owner_id` null cuando el principal es organización; nunca `""`.
- `deletePet` → `m08_archive_pet`.
- Avatar → `m08_set_pet_avatar_asset` (no escribir asset en `photo_url`).
- Create: create RPC → profile → health; fallo secundario = `PetCreatePartialException` (no borrar).
- Perfil público ajeno: lista vacía (sin `m08_list_public_profile_pets`).
- `PetSupabaseDataSource` create/update/delete queda sin cablear desde DataProvider.

## ViewModels

- `PetDetailViewModel.canManage` = `canUpdate || canArchive` del context.
- `PetFormViewModel` edita con `canUpdate`; avatar vía `setPetAvatarAsset`.
- `UserPublicProfileViewModel` no usa `observePets()` global.
- `MyPetsViewModel` sigue con `observePetsForOwner`; el adapter lista accesibles.

## Gate

`scripts/ci/m08_stage4b_quality_checks.sh`

## Siguiente

Etapa **4C** — integración local + smoke APK. Staging sigue bloqueado.
