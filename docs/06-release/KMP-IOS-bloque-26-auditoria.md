# KMP-IOS — Bloque 26 auditoría (Pet archive / lifecycle)

Fuente: `035`/`036` — `m08_archive_pet`, `m08_restore_pet`, `m08_mark_pet_deceased`.

## CONTRACTS

| Contrato | RPC | Cap |
| -------- | --- | --- |
| PET_ARCHIVE | `m08_archive_pet(p_pet_id, p_reason?)` | `pet.archive` |
| PET_RESTORE | `m08_restore_pet(p_pet_id)` | `pet.restore` |
| PET_DECEASED | `m08_mark_pet_deceased(p_pet_id, p_reason?)` | `pet.mark_deceased` |

## VALID_TRANSITIONS

- ACTIVE → ARCHIVED (archive)
- ARCHIVED → ACTIVE (restore)
- ACTIVE → DECEASED (mark deceased; UI solo ACTIVE)
- DECEASED → * bloqueado

## OWNER_AUTH

Backend capability / RLS. PRINCIPAL defaults. No hard delete.

## EFFECTS

| Área | Efecto |
| ---- | ------ |
| PUBLIC_VISIBILITY | M08 no toca passport; M14 independiente |
| HEALTH | updates requieren ACTIVE (`PET_NOT_ACTIVE`) |
| ADOPTION | no adoptable si ARCHIVED/DECEASED |
| LOST_FOUND | sin gate por pets.status |

Hard delete = NO.
