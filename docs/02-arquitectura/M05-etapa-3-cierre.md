# M05 — Cierre Etapa 3: Persistencia, Storage, RLS y RPC

**Fecha:** 2026-07-16  
**Rama:** `m05/etapa-3-persistencia-storage-rls-rpc`  
**Módulo:** M05 — Archivos, Media y Documentos  
**Estado de entrada:** Etapa 2 aprobada y consolidada (`71210f788839e09f5eff99b440059152039c035c`)  
**Spec:** `docs/03-modulos/M05-Etapa-3-Persistencia-Storage-RLS-y-RPC.md`  
**Producto:** LeoVer

---

## 1. Rama y commits

| Ref | SHA / nota |
|-----|------------|
| Commit base Etapa 2 | `71210f788839e09f5eff99b440059152039c035c` |
| Rama de trabajo | `m05/etapa-3-persistencia-storage-rls-rpc` |
| WIP GPS/mapas/pagos | **No** incorporado |
| Merge a `main` | **No** |
| Etapa 4 / M06 | **No** iniciados |
| Producción | **No** usada |
| Validación remota 024 | **PENDIENTE DE VALIDACIÓN REMOTA** |

---

## 2. Migración

| Ítem | Valor |
|------|-------|
| Archivo | `supabase/migrations/024_m05_file_assets_storage_foundation.sql` |
| Numeración | Consecutiva a `023` |
| Edición de `001`–`023` | **No** |
| Remoto / staging | **PENDIENTE DE VALIDACIÓN REMOTA** (incluye `014`–`023` heredados) |

### 2.1 Bucket legacy `leover` (prioridad crítica)

- Lectura pública conservada (`leover_public_read`).
- Políticas `leover_authenticated_upload|update|delete` **eliminadas**.
- Objetos legacy **no** borrados ni movidos.
- URLs públicas existentes **sin cambio**.
- Nuevos uploads M05 a `leover` **bloqueados** (bucket lógico `LEGACY_LEOVER_READ_ONLY`; `m05_logical_bucket` nunca devuelve `leover` para propósitos activos).

### 2.2 Buckets creados

| Bucket | Público | Uso |
|--------|---------|-----|
| `public-media` | sí | Media pública tipada (pets, posts, adopción, etc.) |
| `organization-documents` | no | Documentos / verificación org |
| `moderation-evidence` | no | Evidencia M04 |
| `support-attachments` | no | Adjuntos de soporte |

**Sin recrear:** `profile-avatars`, `organization-media`.

### 2.3 Tablas

- `file_assets` — ownership USER / ORGANIZATION / PLATFORM (XOR), purpose, visibility, status, retención, legal hold  
- `file_asset_versions` — bucket físico allowlisted (sin `leover`), path verificado  
- `file_asset_links` — vínculos lógicos a recursos; soft unlink  
- `file_upload_sessions` — sesión tipada + expiry  
- `file_access_audit` — auditoría de accesos (sin tokens)  
- `file_retention_policies` — retención centralizada por purpose  

RLS **deny-by-default** para INSERT/UPDATE/DELETE en tablas; SELECT acotado; mutaciones vía RPC.

### 2.4 RPCs (SECURITY DEFINER, `search_path = public`, actor `auth.uid()`)

Assets: `create_file_asset_draft`, `get_file_asset`, `list_file_assets_for_resource`, `link_file_asset`, `unlink_file_asset`, `request_file_asset_delete`, `restore_file_asset`.

Upload: `create_file_upload_session`, `transition_file_upload_session`, `update_file_upload_progress`, `complete_file_upload`, `fail_file_upload`, `cancel_file_upload`.

Acceso: `resolve_public_file_asset`, `request_file_signed_url` (devuelve bucket/path/TTL; **no** URL persistida).

Retención: `place_file_legal_hold`, `release_file_legal_hold`, `can_physically_delete_file_asset`.

Auditoría: `list_file_access_audit`.

Path: servidor construye vía `m05_build_storage_path`; path cliente arbitrario distinto → `CLIENT_STORAGE_PATH_DENIED`.

---

## 3. Android

| Archivo | Rol |
|---------|-----|
| `SupabaseFileAssetRepository` | RPC assets |
| `SupabaseFileUploadRepository` | RPC upload / transición |
| `SupabaseFileDownloadRepository` | Público + signed temporal vía Storage API |
| `SupabaseFileAccessRepository` | Autorización dominio + get asset |
| `SupabaseFileRetentionRepository` | Legal hold / physical delete check |
| `M05SupabaseRpcSupport` | Mapeo JSON → dominio |
| `LegacyFileReferenceAdapter` | Lectura legacy; sin ownership ni upload |
| `FileAssetReferenceResolver` | Refs lógicas M04 (`assetId`); rechazo base64 / signed persistido |
| `DataProvider` | `useSupabase=true` → Supabase M05; `false` → mocks |

Servicios legacy (`SupabaseStorageService`, `ProfileAvatarStorageService`, `OrganizationMediaStorageService`) **intactos**.

Sin pantallas ni navegación nuevas. Sin GPS/mapas/pagos. Sin base64. Sin persistir signed URLs.

---

## 4. Integración M04 (base)

- Propósitos sensibles habilitados a nivel de persistencia: `MODERATION_EVIDENCE`, `ORGANIZATION_VERIFICATION_DOCUMENT`, `SUPPORT_ATTACHMENT`.  
- M04 puede transportar `assetId` lógico (`FileAssetReferenceResolver.toM04LogicalRef`).  
- Sensibles **nunca** `PUBLIC`; bucket `leover` **no** usado para evidencia/verificación/soporte.  
- Sin UI nueva; sin cambiar decisiones de moderación.

---

## 5. Pruebas y calidad

| Control | Resultado |
|---------|-----------|
| Doc SQL/calidad | `docs/04-calidad/M05-pruebas-persistencia-storage-rls-rpc.md` |
| Suite unitaria | **338** tests, **0** failures, **0** errors (327 Etapa 2 + 11 Etapa 3) |
| `assembleDebug` | **SUCCESS** |
| `testDebugUnitTest` | **SUCCESS** |
| `lintDebug` | **SUCCESS** |

No se afirma antivirus, EXIF strip, thumbnails ni MIME sniffing profundo.

---

## 6. Seguridad (cumplimiento Etapa 3)

- Actor siempre `auth.uid()` en RPC.  
- Cliente no elige bucket sensible.  
- Servidor revalida purpose, owner, bucket, path, MIME, tamaño y permisos.  
- Roles M03 solo dentro de su organización; AccountType / `active_modules` no conceden acceso.  
- Signed URL temporal en memoria de cliente; no columna de URL firmada.  
- Unlink / soft delete / physical delete controlado + legal hold.

---

## 7. Deuda pendiente

| Ítem | Estado |
|------|--------|
| Aplicar/validar `024` en staging | **PENDIENTE DE VALIDACIÓN REMOTA** |
| Migraciones `014`–`023` staging | **PENDIENTE DE VALIDACIÓN REMOTA** |
| UI / navegación archivos | Etapa 4+ |
| Migración masiva `*_url` → assetId | Gradual, no esta etapa |
| Procesamiento imagen / antivirus | Futuro (no afirmado) |
| Edge Functions | No incluidas |

---

## 8. Checklist Etapa 3

- [x] Rama desde commit base Etapa 2  
- [x] Migración `024` únicamente (sin editar `001`–`023`)  
- [x] `leover` read-only; sin borrado/movimiento de objetos legacy  
- [x] Buckets tipados creados; avatars/org media no recreados  
- [x] Tablas + RLS deny-by-default + RPC SECURITY DEFINER  
- [x] Repos Supabase + DataProvider + adaptadores legacy  
- [x] Integración base referencias M04  
- [x] Sin pantallas / Etapa 4 / M06 / merge a `main` / producción  
- [x] Calidad local assemble / tests / lint  
- [x] Docs de cierre + calidad  

---

## 9. Parada

Etapa 3 **cerrada a nivel de implementación local** (SQL + Android + pruebas).

**No** iniciar Etapa 4.  
**No** iniciar M06.  
**No** merge a `main`.  
**No** afirmar validación remota de `024` sin evidencia.
