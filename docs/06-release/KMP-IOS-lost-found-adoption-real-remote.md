# KMP-IOS — Lost/Found + Adoption REAL_REMOTE (Bloque 7)

## Runtime

```text
SharedRemoteRuntime (internal)
  SupabaseClient { Auth + Postgrest + SecureStorageSessionManager }
  → AuthRepository
  → UserProfileRepository
  → SharedPetsRepository
  → LostFoundRepository (RemoteLostFoundRepository)
  → AdoptionRepository (RemoteAdoptionRepository)
```

**SUPABASE CLIENT COUNT = 1.** Misma sesión / Auth / Postgrest / Keychain.

## Lost / Found

| Pieza | Implementación |
| ----- | -------------- |
| Contrato | `LostFoundRepository` (sin segunda interfaz) |
| Repo | `RemoteLostFoundRepository` + `UnconfiguredLostFoundRepository` |
| Gateway | `SupabaseLostFoundRemoteGateway` / `FakeLostFoundRemoteGateway` |
| Fuente list | `lost_found_posts` SELECT order `created_at` DESC |
| Fuente detail | `lost_found_posts` SELECT by `id` |
| Mapper | `RemoteLostFoundMapper` → SAFE models |
| Filtros | ALL / LOST / FOUND (client-side sobre lista remota) |
| MEDIA_RENDERING | **PARTIAL** (`hasPhoto` + placeholder UI) |

## Adoptions

| Pieza | Implementación |
| ----- | -------------- |
| Contrato | `AdoptionRepository` |
| Repo | `RemoteAdoptionRepository` + `UnconfiguredAdoptionRepository` |
| Gateway | `SupabaseAdoptionRemoteGateway` / `FakeAdoptionRemoteGateway` |
| Fuente list | RPC `m09_list_published_adoptions` |
| Fuente detail | RPC `m09_get_adoption` (`p_adoption_id`) |
| Mapper | `RemoteAdoptionMapper` |
| Visibilidad | Backend + `AdoptionStatusRules.isPubliclyVisible` |
| MEDIA_RENDERING | **PARTIAL** |

## Host iOS

`PocIosViewController` → repos desde `SharedRemoteRuntime` únicamente.

```text
SESSION / PROFILE / PETS / LOST_FOUND / ADOPTIONS = REAL_REMOTE
Fake* host = NO
```

Sin config usable → Unconfigured* con `dataMode = REAL_REMOTE` (Unavailable). Sin fallback fake.

## Privacidad

- Solo `ApproximateLocation` (locality / region / country opcionales)
- Sin lat/lng, contact, email, userId, organizationId en modelos UI
- `publicCode` solo desde `public_code`
- `publisherDisplayName` solo desde campos públicos (`author_name` / `publisher_name`)

## Fuera de alcance KMP-7

Publicar perdido/encontrado/adopción, postulaciones, cámara, GPS, APNs, uploads, edición remota, SQL/schema.
