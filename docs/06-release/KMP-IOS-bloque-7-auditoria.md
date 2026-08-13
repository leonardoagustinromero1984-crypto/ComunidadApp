# KMP-IOS — Bloque 7 auditoría (Lost/Found + Adoptions REAL_REMOTE)

**HEAD base esperado:** `296ec88238f3c37907336fc7d98362b850e2689e`

**KMP-6:** CLOSED GREEN — GitHub Actions KMP iOS Validation #8 PASS (macOS real)

## A — Lost / Found (Android productivo)

| Clave | Valor exacto |
| ----- | ------------ |
| REMOTE_SOURCE_LIST | PostgREST table `lost_found_posts` (`SupabaseTables.LOST_FOUND`) — `.select { order("created_at", DESC) }` |
| REMOTE_SOURCE_DETAIL | Misma tabla; Android resuelve por cache de lista. KMP-7: SELECT por `id` |
| AUTH_REQUIREMENT | `authenticated` (RLS `lost_found_select_authenticated`); sin grant anon SELECT |
| RLS | Select autenticado; mutaciones solo `author_id = auth.uid()` |
| DTO Android | `LostFoundRow` → `LostFoundPost` (`parseLostFound`) |
| DTO KMP | `RemoteLostFoundRow` (internal) |
| STATUS_MAPPING | `ACTIVE`/`RESOLVED`/`CLOSED` → enums KMP. Desconocido → **omitir** (no forzar ACTIVE) |
| TYPE_MAPPING | `LOST`/`FOUND`. Desconocido → **omitir** (no forzar LOST) |
| LOCATION_MAPPING | Texto `location` → `ApproximateLocation.locality`. Coords **no** van a UI SAFE |
| MEDIA_MAPPING | `photo_url` no vacío → `hasPhoto=true`. URL firmada: **PARTIAL** |
| PUBLIC_CODE_MAPPING | `public_code` → `publicCode` (null si ausente) |
| PUBLISHER_MAPPING | `author_name` → `publisherDisplayName`. Nunca `author_id` / contact |
| RPC/view list | **Ninguno** en path Android productivo |
| M09 | **No aplica** a Lost/Found |

### Privacidad (SAFE)

No mapear a UI: `contact_info`, `latitude`, `longitude`, `author_id`, email/teléfono.
`LostFoundId` es opaco para UI (UUID interno ok para navegar; no imprimir).

## B — Adopciones (Android productivo)

| Clave | Valor exacto |
| ----- | ------------ |
| REMOTE_SOURCE_LIST | RPC `m09_list_published_adoptions` (sin params) |
| REMOTE_SOURCE_DETAIL | RPC `m09_get_adoption` param `p_adoption_id` |
| AUTH_REQUIREMENT | `execute` grant a `authenticated` (no anon) |
| RLS / definer | RPC SECURITY DEFINER; list solo `PUBLISHED`; get según reglas M09 |
| DTO Android | `AdoptionPublicationRow` → `AdoptionPost` (`toAdoptionPost`) |
| DTO KMP | `RemoteAdoptionPublicationRow` (internal) |
| STATUS_MAPPING | `DRAFT`/`PUBLISHED`/`ADOPTED`/`CLOSED`; legacy `AVAILABLE`→`PUBLISHED`. `PAUSED`/desconocido → **omitir** |
| PET_MAPPING | Denormalizado en fila (`name`, `species`, `sex`, ages). Sin join live a `pets` |
| LOCATION_MAPPING | `location_text` ?: `location` → `ApproximateLocation.locality` |
| MEDIA_MAPPING | `photo_url` → `hasPhoto`. Rendering URL: **PARTIAL** |
| PUBLISHER_MAPPING | `publisher_name` → `publisherDisplayName`. No `publisher_id` / org id |
| PUBLIC_CODE | `public_code` → `publicCode` |
| Visibilidad client | `AdoptionStatusRules.isPubliclyVisible` (defensa; backend manda) |
| SharedPets | Sin relación de lectura |

## Fuentes Android auditadas

- `LostFoundSupabaseDataSource` / `SupabaseLostFoundRepository`
- `SupabaseAdoptionM09RemoteDataSource` / `SupabaseAdoptionRepository`
- `SupabaseMappers.kt`, `AdoptionPublicationDtos.kt`
- Migrations `005` (LF RLS), `037` (M09 RPCs), `081` (`public_code`)

## WIP preservado (no tocar en KMP-7)

M09 decoding / `SupabaseRowDecoding` / `public_code` Android / `M29` docs — working tree aparte.
