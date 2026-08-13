# KMP-IOS — Bloque 16 auditoría (Pet create)

Fuente: shared KMP — `m08_create_pet_with_principal` + M05 `PET_AVATAR`.

## CREATE CONTRACT

| Paso | RPC / media |
| ---- | ----------- |
| Create | `m08_create_pet_with_principal` (`p_organization_id` / `p_microchip_id` = null) |
| Avatar upload | M05 `create_file_upload_session` purpose `PET_AVATAR`, resource `PET` |
| Bind avatar | `m08_set_pet_avatar_asset` |

## SEMÁNTICA

1. Validar draft (`PET_NAME_REQUIRED` / length).
2. Auth requerida.
3. Create pet.
4. Refresh list tick.
5. Si hay `avatarFile`: upload M05 → `setPetAvatarAsset`.
6. `Success(avatarAttached=true)` o `PartialSuccess` si media falla post-create (mascota queda creada; no se finge foto).

## ERRORES

| Código | Mensaje UI |
| ------ | ---------- |
| `PET_NAME_REQUIRED` | El nombre de la mascota es obligatorio. |
| `FORBIDDEN` | No tenés permiso para esta acción. |
| `PET_AVATAR_ASSET_NOT_FOUND` | No encontramos el archivo de avatar. |
| `PET_AVATAR_PURPOSE_INVALID` | El archivo no es un avatar de mascota válido. |

## M05

`M05PetAvatarMediaRules`: PUBLIC / USER / PET / 8MiB jpeg|png|webp — mismo pipeline que LOST_FOUND_MEDIA.
