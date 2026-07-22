# M08 — Etapa 6: fallecimiento, duplicados y fotos

**Producto:** LeoVer  
**Módulo:** M08 — Mascotas y responsables  
**Fecha:** 2026-07-21  
**Rama:** `m08/etapa-6-fallecimiento-duplicados-fotos`  
**Estado formal:**

```text
M08 ETAPA 6 — FALLECIMIENTO, DUPLICADOS Y FOTOS LISTOS
SMOKE INTEGRAL M08 — PENDIENTE
DEFECTOS DIFERIDOS — BACKLOG
PRODUCCIÓN NO MODIFICADA
```

Restricciones respetadas: sin cambios en migraciones 001–036, sin migración 037, sin SQL remoto, sin escritura de `photo_url`, sin autorización por `ownerId`, sin declarar smoke PASS.

---

## 1. Alcance entregado

| Capacidad | Entrega |
|---|---|
| Marcar fallecida | `PetRepository.markPetDeceased` + diálogo en `PetDetail` (ACTIVE + `canMarkDeceased`) |
| Restaurar | `PetRepository.restorePet` + diálogo (ARCHIVED + `canRestore`) |
| Historial de estado | `PetRepository.listStatusHistory` + `PetStatusHistoryScreen` (`pet_status_history/{petId}`) |
| Duplicados privados | `detectDuplicateCandidates` + aviso en Form (RPC scoped; sin PII ajena) |
| Microchip conflicto | Mensaje fijo vía `M08PetErrorMapper` |
| Avatar | pick → M05 `PET_AVATAR` → `m08_set_pet_avatar_asset`; gate `canManageMedia` |
| Badges / gating | ARCHIVED/DECEASED en listado y detalle; bloqueo de edit/transfer/resp/auth cuando DECEASED |

**No entregado (backlog documentado):** pantalla completa de galería pet (`PetPhotoGalleryScreen`). Ver auditoría Etapa 6 — GALLERY GAP (ACL M05 ≠ `pet.manage_media`).

---

## 2. Navegación

| Ruta | Destino |
|---|---|
| `PET_STATUS_HISTORY` | `pet_status_history/{petId}` → `PetStatusHistoryScreen` |

Rutas Etapa 5 intactas. `petId` URL-encoded.

---

## 3. Contratos reutilizados (sin SQL nuevo)

- `m08_mark_pet_deceased`, `m08_restore_pet`, `m08_archive_pet`
- `m08_detect_pet_duplicate_candidates` (scoped)
- `m08_set_pet_avatar_asset` + M05 upload
- `m08_get_pet_access_context`
- SELECT `pet_status_history` vía remote DS

---

## 4. Errores mapeados

`PET_ALREADY_DECEASED`, `PET_ALREADY_ARCHIVED`, `PET_DECEASED_CANNOT_ARCHIVE`, `PET_DECEASED_CANNOT_RESTORE`, `PET_NOT_ARCHIVED`, `PET_AVATAR_*`, `PET_MICROCHIP_ACTIVE_CONFLICT` (+ aliases `ALREADY_DECEASED` / `INVALID_DECEASED_DATE` si aparecen).

---

## 5. Calidad

- Tests: `MarkPetDeceasedViewModelTest`, `PetStatusHistoryViewModelTest`, `M08Stage6StaticGuardsTest`
- Script: `scripts/ci/m08_stage6_quality_checks.sh`
