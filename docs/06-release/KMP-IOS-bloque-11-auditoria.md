# KMP-IOS — Bloque 11 auditoría (Adoption Publish)

Fuente: `HEAD` comprometido (`81ac1bff…`), **no** WIP M09 local.

## ADOPTION_CREATE_CONTRACT

| Campo | Valor real |
| ----- | ---------- |
| RPC | `m09_create_adoption_publication` |
| Params | `p_pet_id`, `p_title`, `p_description`, `p_requirements`, `p_location_text`, `p_publish` |
| Android | `SupabaseAdoptionM09RemoteDataSource.create` + `CreateAdoptionParams` |
| UI productiva | `AdoptionFormViewModel` (no legacy PublishViewModel) |

## ADOPTION_PUBLISH_CONTRACT

- Un solo RPC con `p_publish=true` → status `PUBLISHED`.
- `p_publish=false` → `DRAFT` (mismo create; no segunda transacción inventada).
- Update/status aparte existen (`m09_update_…`, `m09_set_adoption_status`) pero **fuera de scope** KMP-11 form mínimo.

## ROLE_RULE

- Actor = sesión autenticada (`auth.uid()` en RPC/RLS).
- Publisher = usuario autenticado; `publisher_organization_id` / shelter vienen del backend si aplica.
- **No** se pide org ID en el form CMP.

## INITIAL_STATUS / PUBLISHED_STATUS

- `DRAFT` si `p_publish=false`
- `PUBLISHED` si `p_publish=true`

## PET_REQUIREMENT

- **Obligatoria** `p_pet_id` (mascota accesible del usuario).
- Foto de publicación = snapshot `pets.photo_url` en backend.

## ORGANIZATION_REQUIREMENT

- **No** requerida en create params del form productivo.

## LOCATION_REQUIREMENT

- Texto aproximado `p_location_text` (opcional).
- Sin lat/lng.

## MEDIA_REQUIREMENT

- **Sin** upload M05 separado en el form productivo Android.
- KMP-11: **ADOPTION MEDIA WRITE = PARTIAL** (lectura KMP-10 intacta; write no fingido).

## CONTACT_POLICY

- No teléfono/email públicos en create.

## RLS

- Escritura vía RPC SECURITY DEFINER / policies M09 existentes.
- Cliente shared: un solo `SupabaseClient` + Postgrest RPC.
