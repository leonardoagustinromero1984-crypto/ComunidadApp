# KMP-IOS — Bloque 21 auditoría (Pet edit)

Fuente: `036_m08_pet_repository_compatibility_rpcs.sql` + Android `UpdatePetProfileParams`.

## UPDATE CONTRACT

| RPC | Uso KMP-21 |
| --- | ---------- |
| `m08_update_pet_profile` | REAL_REMOTE edit |
| `m08_set_pet_avatar_asset` | avatar post-edit (M05 PET_AVATAR) |
| `m08_update_pet_health` | **DEFERRED** (no historia clínica) |
| `m08_archive_pet` / delete | **DEFERRED** |

## FIELD CLASS

| Campo | Clase |
| ----- | ----- |
| name, species, breed, sex, size, description, age_years/months, color | EDITABLE |
| microchip | EDITABLE (enviado null si no se edita) |
| avatar | EDITABLE (M05) |
| birth_date, location_text, visibility, hard delete | UNSUPPORTED |
| health block | DEFERRED |

## OWNERSHIP

Backend `m08_require_capability(pet.update)` — no ownerId desde form.

## PARTIAL SUCCESS

Perfil OK + avatar fail → PartialSuccess (texto conservado).
