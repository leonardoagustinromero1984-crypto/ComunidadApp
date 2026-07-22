# M08 — Etapa 6: auditoría de contratos (fallecimiento, duplicados y fotos)

**Producto:** LeoVer  
**Módulo:** M08 — Mascotas y responsables  
**Fecha:** 2026-07-21  
**Rama:** `m08/etapa-6-fallecimiento-duplicados-fotos`  
**Alcance de esta auditoría:** solo lectura de contratos existentes (Android + SQL 035/036). Sin migración 037, sin SQL remoto, sin producción.

**Estado formal:**

```text
M08 ETAPA 6 — AUDITORÍA DE CONTRATOS LISTA
SMOKE INTEGRAL M08 — PENDIENTE
DEFECTOS DIFERIDOS — BACKLOG
PRODUCCIÓN NO MODIFICADA
```

---

## 1. READY (consumir tal cual)

| Capacidad | Contrato | Superficie Android actual | Acción Etapa 6 |
|---|---|---|---|
| Archivar | `m08_archive_pet` | `PetRepository.deletePet` → `LegacyPetRepositoryAdapter` | Mantener |
| Contexto de acceso | `m08_get_pet_access_context` | `PetRepository.getPetAccessContext` con `canMarkDeceased` / `canRestore` / `canArchive` / `canManageMedia` / `canViewHistory` | Usar para gating UI |
| Perfil / microchip | `m08_update_pet_profile` | `PetRepository.updatePet` | Mensaje seguro ante `PET_MICROCHIP_ACTIVE_CONFLICT` |
| Avatar | `m08_set_pet_avatar_asset` + M05 `FileUploadCoordinator` `PET_AVATAR` | `PetRepository.setPetAvatarAsset` + Form upload | Reforzar validación/errores; **nunca** escribir `photo_url` |

---

## 2. PARTIAL (extender solo Android, sin SQL)

| Capacidad | Contrato remoto | Estado actual | Acción Etapa 6 |
|---|---|---|---|
| Marcar fallecida | `m08_mark_pet_deceased` | Existe en `PetM08RemoteDataSource` / `changeLifecycleStatus` del dominio | Exponer en `PetRepository` + UI (diálogo + Detail) |
| Restaurar | `m08_restore_pet` | Idem | Exponer en `PetRepository` + UI (ARCHIVED + `canRestore`) |
| Historial de estado | `SELECT pet_status_history` vía `listStatusHistory` | Idem | Exponer + pantalla/sección + ruta |
| Duplicados privados | `m08_detect_pet_duplicate_candidates` | Idem (RPC scoped a `m08_actor_can_read_pet`) | Exponer + aviso privado en Form (sin PII ajena) |

Códigos SQL relevantes (035), a mapear en `M08PetErrorMapper`:

- `PET_ALREADY_DECEASED`
- `PET_ALREADY_ARCHIVED`
- `PET_DECEASED_CANNOT_ARCHIVE`
- `PET_DECEASED_CANNOT_RESTORE`
- `PET_NOT_ARCHIVED`
- `PET_AVATAR_ASSET_NOT_FOUND`
- `PET_AVATAR_PURPOSE_INVALID`
- `PET_MICROCHIP_ACTIVE_CONFLICT` (mensaje UX fijo, sin datos de otra mascota)

Aliases opcionales (no aparecen como códigos literales en 035; se mapean por robustez si el backend los emite): `ALREADY_DECEASED`, `INVALID_DECEASED_DATE`.

---

## 3. GALLERY GAP (BACKLOG — no inventar SQL)

Existen en M05:

- `list_file_assets_for_resource`
- `link_file_asset`
- `unlink_file_asset`
- propósito `PET_GALLERY` / relación `GALLERY`

**Por qué no alcanza para UX de mascota en Etapa 6:**

1. No hay fachada pet-específica (listar/añadir/quitar fotos de galería con `pet.manage_media`).
2. El ACL de escritura M05 **no** equivale a `pet.manage_media` del contexto M08: mutar galería vía RPCs genéricos sería inseguro o inconsistente con el modelo de capacidades de mascota.
3. No hay pantalla ni ViewModel de galería pet; inventar RPCs o migraciones está fuera de alcance (máx. migración 036).

**Decisión Etapa 6:** documentar como backlog; **no** implementar `PetPhotoGalleryScreen` completa. Sí se entrega avatar (`PET_AVATAR` → `m08_set_pet_avatar_asset`), fallecimiento, historial, restore, microchip y duplicados privados.

Criterio de re-evaluación futura: solo si `FileAssetRepository` + autorización quedan alineados a `canManageMedia` / `canRead` **sin** RPCs nuevos, o con contratos M08 explícitos.

---

## 4. Restricciones de implementación (recordatorio)

- No crear migración 037 ni modificar 001–036.
- No `db push` / ops remotas / producción.
- No imprimir secrets / `service_role`.
- No escribir `photo_url` directamente.
- No búsqueda global duplicada en `public.pets` (usar solo RPC scoped).
- No autorizar por `ownerId`.
- No declarar smoke PASS; mantener `M08-SMOKE-001` OPEN.
- No iniciar Etapa 7; no merge a `main`; no trackear APK.
