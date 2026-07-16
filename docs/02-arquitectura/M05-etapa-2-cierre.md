# M05 — Cierre Etapa 2: Contratos de assets, ownership, validación y rutas

**Fecha:** 2026-07-16  
**Rama:** `m05/etapa-2-contratos-assets-ownership-validacion-rutas`  
**Módulo:** M05 — Archivos, Media y Documentos  
**Estado de entrada:** Etapa 1 consolidada (`39aa369d8e5e45f41cebe6b769f6994d12a255e4`)  
**Spec:** `docs/03-modulos/M05-Etapa-2-Contratos-Assets-Ownership-Validacion-y-Rutas.md`

---

## 1. Rama y commits

| Ref | SHA / nota |
|-----|------------|
| Etapa 1 consolidada (base) | `39aa369d8e5e45f41cebe6b769f6994d12a255e4` |
| Rama de trabajo | `m05/etapa-2-contratos-assets-ownership-validacion-rutas` |
| WIP GPS/mapas/pagos | **No** incorporado |
| Merge a `main` | **No** |
| Etapa 3 / M06 | **No** iniciados |
| SQL / buckets / RLS | **No** modificados |
| Corrección `leover` | **No** (deuda Etapa 3) |

---

## 2. Archivos creados

### Dominio

| Archivo |
|---------|
| `domain/files/FileAssetPurpose.kt` (propósitos + `FilePurposePolicy` + buckets lógicos) |
| `domain/files/FileAssetVisibility.kt` (visibilidad, estados, reglas) |
| `domain/files/FileAssetOwner.kt` (ownership único + dual reject) |
| `domain/files/FileAsset.kt` (asset, versión, link, upload/access/retention models + rules) |
| `domain/files/FileValidation.kt` (sanitizer, MIME/size/count, session, signed, retention) |
| `domain/files/FilePath.kt` (path builder tipado + legacy recognize) |
| `domain/files/FileContracts.kt` (legacy URL/path references) |
| `domain/files/authorization/FileAuthorization.kt` |

### Repositorios

| Archivo |
|---------|
| `data/repository/FileRepositories.kt` (interfaces + mocks deterministas) |

### Pruebas

| Archivo / cobertura |
|---------------------|
| `FileAssetRulesTest` |
| `FileAssetVersionRulesTest` |
| `FileOwnershipRulesTest` |
| `FilePurposeAndVisibilityTest` (`FilePurposePolicyTest`, `FileVisibilityRulesTest`) |
| `FileValidationRulesTest` (+ `FileNameSanitizerTest`) |
| `FilePathSessionRetentionLegacyTest` (path, session, signed, retention, legacy) |
| `authorization/FileAuthorizationTest` |
| `FileRepositoryMocksTest` |

### Docs

| Archivo |
|---------|
| `docs/03-modulos/M05-Etapa-2-Contratos-Assets-Ownership-Validacion-y-Rutas.md` |
| `docs/02-arquitectura/M05-etapa-2-cierre.md` (este) |

## 3. Archivos modificados

| Archivo | Cambio |
|---------|--------|
| `StoragePaths.kt` | Documenta compatibilidad; `isLegacyStylePath`; paths legacy intactos |
| `DataProvider.kt` | Expone repos M05 siempre vía mocks (sin Supabase nuevo); Storage legacy intacto |

---

## 4. Modelo de assets

- `FileAsset`: metadatos + owner + purpose + visibility + status + retención/legal hold.  
- `FileAssetVersion`: bucket lógico + path + filenames + MIME declared/detected + size.  
- Sin binarios, bytes ni base64 en modelos.  
- Signed URL **no** forma parte del asset (solo `FileSignedAccess` temporal).

---

## 5. Ownership

- Único: `USER` | `ORGANIZATION` | `PLATFORM`.  
- `rejectDual` niega user+org (+platform) simultáneos.  
- Propósito valida `allowedOwnerKinds`.  
- Roles M03 solo dentro de su organización (`FileAuthorization`).  
- `AccountType` / `active_modules` → `grantsFromAccountTypeOrModules() = false`.

---

## 6. Propósitos y buckets lógicos

Propósitos centralizados en `FilePurposePolicy` (extensiones, MIME, tamaño, cantidad, visibilidad, bucket, path template).

Buckets lógicos:

```text
PROFILE_AVATARS, ORGANIZATION_MEDIA, PUBLIC_MEDIA,
ORGANIZATION_DOCUMENTS, MODERATION_EVIDENCE, SUPPORT_ATTACHMENTS,
LEGACY_LEOVER_READ_ONLY
```

- Sensibles (`MODERATION_EVIDENCE`, `ORGANIZATION_VERIFICATION_DOCUMENT`, `SUPPORT_ATTACHMENT`) **nunca** `PUBLIC`.  
- `LEGACY_LEOVER_READ_ONLY` **no** acepta upload M05 nuevo.

---

## 7. Visibilidad

`PUBLIC` | `OWNER_ONLY` | `ORGANIZATION_PRIVATE` | `AUTHORIZED_STAFF` | `RESOURCE_PARTICIPANTS` | `SIGNED_LINK_ONLY`  
`SIGNED_LINK_ONLY` no implica acceso sin autorización previa.

---

## 8. Validadores

- Nombre: path strip, traversal, control chars, doble extensión peligrosa, allowlist.  
- Extensiones peligrosas (exe, apk, svg, …) rechazadas.  
- MIME: allowlist; **detected prevalece** sobre declared; mismatch → deny.  
- Tamaño/cantidad por propósito.  
- **No** se afirma antivirus, EXIF strip, thumbnails ni procesamiento real (`*Available() = false`).

---

## 9. Rutas

- `FilePathBuilder` genera paths tipados con `assetId`.  
- `StoragePaths` permanece fuente de paths legacy.  
- Cliente no elige bucket sensible (`clientMustNotChooseSensitiveBucket`).  
- Paths no son URLs; sin `..`.

---

## 10. Signed URLs

- TTL: `PUBLIC_RESOLUTION` / `STANDARD_PRIVATE` (≤60m) / `SENSITIVE_SHORT` (≤10m, recomendado 5m).  
- No persistir; no loguear token.  
- Asset eliminado no firma.

---

## 11. Upload sessions

Estados `CREATED`…`COMPLETED`/`FAILED`/`CANCELLED`/`EXPIRED`.  
Progreso 0–100; cancelación idempotente; doble submit rechazado; reloj inyectable en mock.

---

## 12. Retención y eliminación

- `UNLINK` no borra físico.  
- Physical delete bloqueado por links activos, legal hold o retención.  
- Políticas por propósito (`FileRetentionRules.defaultPolicy`).

---

## 13. Compatibilidad legacy

- `LegacyPublicUrlReference` / `LegacyStoragePathReference`.  
- Solo lectura; sin ownership ni upload; `content://` rechazado.  
- Reconocimiento de paths `StoragePaths` / `leover` media.

---

## 14. Repositorios y mocks

Interfaces: Asset / Upload / Download / Access / Retention.  
Mocks deterministas: IDs `asset-N`, paths tipados, signed mock `https://mock.leover.local/...`, **nunca** `content://` como resultado final.  
`DataProvider`: mocks M05 siempre en Etapa 2; `storageService` / avatar / org media legacy sin cambio.

---

## 15. Pruebas y calidad

| Control | Resultado |
|---------|-----------|
| Suite unitaria | **327** tests, **0** failures, **0** errors (261 previas + 66 M05 Etapa 2) |
| `assembleDebug` | **SUCCESS** |
| `testDebugUnitTest` | **SUCCESS** |
| `lintDebug` | **SUCCESS** |

---

## 16. Deuda pendiente

| Ítem | Estado |
|------|--------|
| Endurecer / migrar bucket `leover` | Etapa 3 |
| Buckets físicos nuevos | Etapa 3 |
| Implementaciones Supabase de repos M05 | Etapa 3+ |
| Procesamiento imagen / antivirus | Futuro (no afirmado) |
| Migración gradual `*_url` → assetId | Etapa 3+ |
| Staging `014`–`023` | **PENDIENTE DE VALIDACIÓN REMOTA** |

---

## 17. Checklist Etapa 2

- [x] Working tree / rama desde commit base  
- [x] Sin SQL, migraciones, buckets, policies, RLS, pantallas  
- [x] Sin Supabase nuevo; sin corregir `leover`  
- [x] Modelo asset/versión/link/upload/access/retention  
- [x] Ownership único; deny dual  
- [x] Propósitos/visibilidad/validadores/rutas centralizados  
- [x] Legacy read-only; signed no persistente  
- [x] Mocks + DataProvider  
- [x] AccountType/modules sin autoridad; M03 scoped a org  
- [x] Evidencia/verificación/soporte sensibles no públicos  
- [x] Tests / build / lint  
- [x] Cierre creado  
- [x] Sin Etapa 3 / M06 / merge a `main`

---

## 18. Parada

Etapa 2 **cerrada a nivel de contratos locales**.

**No** iniciar Etapa 3.  
**No** corregir `leover` vía SQL.  
**No** crear buckets.  
**No** merge a `main`.
