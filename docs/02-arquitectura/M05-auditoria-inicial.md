# LEOVER — M05 Auditoría inicial (Archivos, Media y Documentos)

**Módulo:** M05 — Archivos, Media y Documentos  
**Etapa:** 1 — Auditoría y diseño  
**Fecha:** 2026-07-16  
**Rama:** `m05/archivos-media-documentos-auditoria`  
**Dependencia:** M04 cerrado a nivel código y calidad local (`M04-cierre-final.md`)  
**Backend oficial:** Supabase (ADR-0001)  
**Alcance:** inventario y diseño; **sin** migraciones, **sin** tablas, **sin** buckets nuevos, **sin** cambios de RLS/policies, **sin** repositorios nuevos, **sin** cambios de pantallas, **sin** Storage administrativo, **sin** M06

**Documentos de entrada (orden leído):**

1. `docs/01-producto/D01-Modulos-y-Orden.md`  
2. `docs/02-arquitectura/M00-cierre-final.md` … `M04-cierre-final.md`  
3. `docs/04-calidad/M04-reporte-validacion-staging.md`  
4. `docs/03-modulos/M05-Archivos-Media-y-Documentos.md`  
5. ADR-0001 … ADR-0005  

---

## 0. Estado Git y calidad

| Ref | Nota |
|-----|------|
| Commit base M04 cierre | `24a6d1ac2b0a88a5e8fddd1b5c4e2707202992ad` |
| Rama | `m05/archivos-media-documentos-auditoria` |
| Working tree al crear rama | Limpio salvo spec M05 untracked (`docs/03-modulos/M05-Archivos-Media-y-Documentos.md`) |
| WIP GPS/mapas/pagos | **No** mezclado |
| Merge a `main` | **No** |
| Migraciones aplicadas / creadas en esta etapa | **Ninguna** |
| Funcionalidad modificada | **Ninguna** (solo este documento) |

| Control | Resultado |
|---------|-----------|
| `assembleDebug` | SUCCESS |
| `testDebugUnitTest` | **261** tests, **0** failures, **0** errors |
| `lintDebug` | SUCCESS |

**Remoto / staging 014–023:** **PENDIENTE DE VALIDACIÓN REMOTA** (bloquea release; no bloquea esta auditoría). No se afirma aplicación remota.

---

## 1. Hallazgo central

Existe una **base parcial de Storage** heredada de M00–M03:

| Capa | Estado |
|------|--------|
| Bucket público legacy `leover` | **Implementado y riesgoso** (escritura authenticated sin ownership de path) |
| Bucket privado `profile-avatars` | **Implementado** (path + RLS ownership) |
| Bucket privado `organization-media` | **Implementado** (path + permiso M03) |
| Metadatos de asset / versiones / retención | **Ausentes** |
| Evidencia administrativa física (M04) | Solo **refs lógicas**; **sin** bucket ni upload |
| Documentos / PDF / adjuntos | **Ausentes** |
| Compresión, thumbnails, EXIF, antivirus | **Ausentes** (no afirmar lo contrario) |

M05 debe **endurecer y unificar** antes de ampliar usos sensibles. El gap crítico de seguridad es el bucket público `leover` con policies solo por `bucket_id`.

---

## 2. Inventario de buckets

| Bucket | Origen | Público | Límite tamaño | MIME allowlist | Uso actual Android |
|--------|--------|---------|---------------|----------------|--------------------|
| `leover` | `002_storage.sql` | **Sí** | No | No | Pets, posts, adoptions, lost/found (URLs públicas persistidas) |
| `profile-avatars` | `017_profile_avatar_storage.sql` | **No** | 5 MiB | jpeg/png/webp | Avatares usuario (path persistido; signed URL temporal) |
| `organization-media` | `019_organizations_foundation.sql` | **No** | 5 MiB | jpeg/png/webp | Logo org (cover API existe, UI parcial) |

**Otros buckets en migraciones 001–023:** ninguno.

**Buckets administrativos / evidencia / verificación / soporte:** **no existen**.

---

## 3. Inventario SQL y policies

### 3.1 Migraciones con Storage

| Migración | Contenido Storage |
|-----------|-------------------|
| `002` | Crea `leover` + 4 policies (SELECT público; INSERT/UPDATE/DELETE authenticated solo por bucket) |
| `017` | Crea `profile-avatars` + helpers path + policies SELECT/INSERT/UPDATE/DELETE con ownership |
| `019` | Crea `organization-media` + path regex + `has_org_permission('organization.update')` en escritura; SELECT miembros |
| `015`/`016` | `users.avatar_path` + RPC de perfil (valida prefijo; más débil que Storage RLS) |
| `022` | `moderation_evidence_refs` / `organization_verification_document_refs` con `storage_path_hint` lógico; rechazo parcial de `http://`/`https://`; **sin** bucket |
| `023` | Sin Storage (solo proyección sensible M04) |
| `001`–`014`, `018`, `020`, `021` | Sin creación de buckets; varias columnas `*_url` legacy |

### 3.2 Policies `storage.objects` (resumen)

| Policy | Bucket | Resumen |
|--------|--------|---------|
| `leover_public_read` | leover | SELECT si `bucket_id = 'leover'` |
| `leover_authenticated_upload/update/delete` | leover | Mutación authenticated **sin** check de path/owner |
| `profile_avatars_*` | profile-avatars | Path `users/{uid}/avatar/...`; SELECT también si avatar visible por reglas de perfil |
| `organization_media_*` | organization-media | Path `organizations/{uuid}/logo|cover/{file}`; write con `organization.update`; read miembros |

No hay policies sobre `storage.buckets`. No hay `GRANT`/`REVOKE` directos sobre `storage.objects` en estas migraciones.

### 3.3 Columnas path / URL (inventario)

| Tabla.columna | Tipo conceptual | Validación SQL de Storage |
|---------------|-----------------|---------------------------|
| `users.profile_image_url` | URL legacy | Ninguna |
| `users.avatar_path` | Path privado | Prefijo en RPC; RLS Storage más fuerte |
| `pets.photo_url` | URL pública | Ninguna |
| `posts.image_url` / `author_image_url` | URL | Ninguna |
| `adoptions.photo_url` | URL | Ninguna |
| `lost_found_posts.photo_url` | URL | Ninguna |
| `shelters` / `foster_homes` / `community_events` / `donation_campaigns` `.photo_url` | URL | Ninguna |
| `service_profiles.photo_url` | URL | Ninguna |
| `shop_products.photo_url` | URL | Ninguna |
| `organizations.logo_path` / `cover_path` | Path privado | No valida existencia/org en UPDATE de fila |
| `moderation_evidence_refs.storage_path_hint` | Hint lógico | Rechaza URL que empiece con http(s) (bypass posibles) |
| `organization_verification_document_refs.storage_path_hint` | Hint lógico | Idem |

**Base64 en tablas:** no hay columnas base64 explícitas.  
**URLs firmadas persistidas en SQL:** no hay columna dedicada; el riesgo está en persistir URLs públicas de `leover` y URLs arbitrarias en columnas legacy.

---

## 4. Inventario Android

### 4.1 Servicios

| Clase | Bucket | Rol |
|-------|--------|-----|
| `ImageStorageService` | — | Interfaz `uploadImage(path, Uri)` |
| `SupabaseStorageService` | `leover` | Upload público; `readBytes`; fuerza `image/jpeg`; construye URL pública; delete previo best-effort |
| `ProfileAvatarStorageService` | `profile-avatars` | Upload path fijo; `createSignedUrl`; `deleteAvatar` (sin callers UI) |
| `OrganizationMediaStorageService` | `organization-media` | Logo/cover; validación de path; signed URL; delete (sin callers UI) |
| `StoragePaths` | — | Paths users/orgs/pets/posts/adoptions/lost_found |

### 4.2 DataProvider / mocks

- `useSupabase = true` → servicios reales.  
- `useSupabase = false` → **null** (no hay mock de Storage).  
- Consecuencia mock: a menudo se persiste `content://…` o path fabricado sin upload.

### 4.3 Selección de archivos

- Photo Picker (`PickVisualMedia` / `ImageOnly`) en perfil, onboarding, pets, publish, org edit.  
- **Sin** picker de documentos (PDF/DOCX).  
- **Sin** FileProvider / cámara / DocumentFile.

### 4.4 Procesamiento

| Capacidad | Estado |
|-----------|--------|
| Compresión / resize | **Ausente** |
| Thumbnails | **Ausente** |
| EXIF strip / orientación | **Ausente** |
| MIME sniffing real | **Ausente** (se declara JPEG) |
| Límite tamaño cliente | **Ausente** (solo bucket privado en server) |
| Progreso byte-level | **Ausente** (solo flags saving) |
| Retry / cancelación upload | **Ausente** / parcial (lifecycle VM) |
| Doble submit | Parcial (UI disable; no mutex universal) |
| Antivirus / scanning | **Ausente** — **no afirmar** |

### 4.5 Logout / logs

- `SessionViewModel.logout` limpia user, permisos, org context, estado admin M04.  
- **No** limpia explícitamente `pendingImageUri`, Coil cache ni signed URLs en VM.  
- `AppLogger` sanitiza tokens/emails; throwables de Storage org pueden filtrar path/URL en mensaje de excepción.

### 4.6 Confirmaciones de autoridad

| Afirmación | Evidencia |
|------------|-----------|
| Sin service role en Android | Solo `SUPABASE_ANON_KEY` en cliente |
| Actor Storage | Sesión Supabase → `auth.uid()` en RLS |
| AccountType / active_modules | No otorgan acceso a buckets; gates de producto aparte |
| Roles M03 | Solo vía `has_org_permission` en `organization-media` |
| Cliente no elige bucket sensible arbitrario | Buckets **hardcodeados** por servicio (bien); `leover` sigue siendo demasiado permisivo en server |
| Path ≠ autorización única | En `leover` **sí** se confía solo en bucket (riesgo); en avatars/org **no** |

---

## 5. Mapa de usos (clasificación)

| Dominio | Clasificación | Notas |
|---------|---------------|-------|
| Usuarios (avatar) | **PARCIAL** | Bucket privado + path; signed URL en edit; sin remove UI; resolver público incompleto |
| Mascotas | **IMPLEMENTADO** + **RIESGO** | Upload a `leover`; URL pública; sin limpieza al borrar pet; no atómico |
| Organizaciones (logo/cover) | **PARCIAL** | Logo OK; cover sin UI; path privado; proyección pública de path vs object privado = mismatch |
| Publicaciones | **IMPLEMENTADO** + **RIESGO** | Imagen opcional en `leover`; fila antes que upload |
| Adopciones | **RIESGO** | Upload puede fallar en silencio; doble upload (adopción + post) |
| Perdidos/encontrados | **RIESGO** | Mismo patrón que adopciones |
| Servicios | **PARCIAL** | `photoUrl` modelado; sin picker/upload propio |
| Moderación / evidencia | **FUTURO** | Solo `ModerationEvidenceRef` lógico (M04) |
| Verificación org | **FUTURO** | Solo document refs lógicos |
| Soporte adjuntos | **FUTURO** | `evidenceRefId` sin pipeline de archivo |
| Mensajes / chat | **AUSENTE** | Texto only |
| Eventos | **MOCK** | `photoUrl` en fixtures; `publishEvent` sin URI |
| Marketplace | **PARCIAL** | `photoUrl` en modelo; creación sin imagen |
| Documentos PDF/DOCX | **AUSENTE** | — |
| Storage admin / evidencia física | **AUSENTE** | Prohibido afirmar implementación |

---

## 6. Clasificación público / privado / sensible

| Clase | Hoy | Debe ser (diseño M05) |
|-------|-----|------------------------|
| Avatar usuario | Privado (bucket) + path | Público **solo** proyección controlada / signed o variante pública explícita |
| Logo/cover org | Privado object; path en proyección pública | Decidir: variante pública o signed backend |
| Pet/post/adoption/LF media | **Público** vía `leover` | Público explícito **con** ownership de path y límites |
| Evidencia moderación | N/A físico | **Sensible** — bucket privado, signed corta, audit |
| Docs verificación | N/A físico | **Sensible** / org-private |
| Soporte PRIVACY/SAFETY | N/A | **Sensible** |
| Adjuntos chat | N/A | Privado participantes |

**Confirmado:** ningún documento sensible **implementado** usa bucket público — porque **aún no hay** documentos sensibles físicos. El riesgo futuro es usar `leover` para ellos (prohibido por diseño).

---

## 7. Riesgos de seguridad y privacidad

| ID | Riesgo | Severidad | Mitigación propuesta (etapas futuras) |
|----|--------|-----------|----------------------------------------|
| R1 | `leover` writable por cualquier authenticated sin ownership de path | **Crítica** | Redesign policies + namespaces; o deprecar hacia buckets tipados |
| R2 | URLs públicas permanentes en columnas `*_url` | Alta | Preferir path + resolver; versionado |
| R3 | Columnas URL sin validación de esquema/host/ownership | Alta | Allowlist + vínculo a `file_assets` |
| R4 | Delete-before-upload puede destruir imagen previa | Media | Upsert atómico / no delete previo |
| R5 | Fila DB antes que upload (posts/pets/adoptions) | Media | Coordinar upload session / rollback |
| R6 | Doble upload adopción/LF → post | Media | Un asset, múltiples links |
| R7 | Sin limpieza de huérfanos | Media | Jobs + unlink policy |
| R8 | MIME declarado ≠ contenido (todo “JPEG”) | Media | Detect MIME; allowlist por propósito |
| R9 | Sin EXIF strip (posible GPS en foto) | Media | Pipeline imagen |
| R10 | Logo path público vs object privado | Media | Signed URL pública controlada o bucket público de solo lectura tipado |
| R11 | M04 hints: bypass whitespace/`data:` | Baja–Media | Validar contra allowlist de buckets/paths M05 |
| R12 | Logs con throwable Storage | Baja | Sanitizar mensajes de excepción |
| R13 | Staging 014–023 pendiente | Release | Heredado |
| R14 | Sin antivirus | Aceptado (ausente) | No inventar; evaluar en diseño futuro |

---

## 8. Duplicaciones y reutilización

### Reutilizar tal cual

- ADR-0001 Storage Supabase; ADR-0003 providers; ADR-0004 cliente Kotlin.  
- `ProfileAvatarStorageService` / `OrganizationMediaStorageService` como patrones de bucket privado + path + signed URL.  
- `StoragePaths` (extender, no duplicar ad-hoc).  
- Photo Picker + Coil.  
- `AppResult` / `AppLogger` / gates M02–M04.  
- Refs lógicas M04 (`ModerationEvidenceRef`, verification docs).

### Reutilizar con adaptación

- `SupabaseStorageService` / bucket `leover` — **endurecer o reemplazar** antes de nuevos usos.  
- Columnas `*_url` — migrar gradualmente a path/asset id.  
- `DataProvider` — mock Storage realista para tests.

### No reutilizar como autoridad

- AccountType / active_modules.  
- Path solo (sin RLS) en `leover`.  
- Bucket público para evidencia/docs sensibles.  
- URLs firmadas como “identidad permanente” del archivo.

---

## 9. Componentes reutilizables (candidatos futuros)

```text
FileAssetRepository / FileUploadCoordinator
FileValidator (size, MIME detect, extension, purpose)
FileNameSanitizer
SignedUrlProvider (TTL por sensibilidad)
ImageProcessor (resize/compress/EXIF strip) — sin afirmar existente
FileCleanupCoordinator (huérfanos)
FileAccessAudit (ops sensibles)
```

No crear un “god repository” que ignore propósito y autorización.

---

## 10. Gaps vs diseño M05

| Requisito M05 | Estado actual |
|---------------|---------------|
| Privado por defecto | Parcial (avatars/org sí; media social no) |
| Separar físico / metadatos / link | Ausente (`file_assets` no existe) |
| No base64 en tablas | Cumplido |
| No persistir signed URLs | Cumplido en avatars/org; N/A en leover (usa public URL) |
| Límites tamaño/MIME | Solo buckets privados |
| Ownership verificable en todos los buckets | **Falla en leover** |
| Evidencia admin física | Ausente (refs OK) |
| Documentos verificación físicos | Ausente |
| Adjuntos soporte/mensajes | Ausente |
| Versiones / retención / quarantine | Ausente |
| Limpieza huérfanos | Ausente |
| Progreso/retry/cancel | Ausente / parcial |
| Scanner antivirus | Ausente |

---

## 11. Propuesta de modelo (solo diseño — **no implementar en Etapa 1**)

Entidades candidatas (confirmar en Etapa 2–3):

```text
file_assets
file_asset_versions
file_asset_links
file_upload_sessions
file_access_audit
file_retention_policies
```

Estados candidatos: `PENDING_UPLOAD` → `UPLOADED` → `READY` | `REJECTED` | `QUARANTINED` | `DELETED` | `FAILED`.

Visibilidad: `PUBLIC` | `OWNER_ONLY` | `ORGANIZATION_PRIVATE` | `AUTHORIZED_STAFF` | `SIGNED_LINK_ONLY`.

Reglas:

1. Un asset tiene bucket + path + checksum + mime detectado.  
2. Dominios referencian `file_asset_id` (o path tipado) — no base64.  
3. Signed URL se genera tras auth; TTL corto para sensibles; **no** se persiste.  
4. M04 evidencia consume links M05; retención puede bloquear delete físico.  
5. Eliminar link ≠ borrar físico si hay retención u otras refs.

---

## 12. Propuesta de buckets

| Bucket | Visibilidad | Propósito |
|--------|-------------|-----------|
| `profile-avatars` | Privado (existente) | Avatares; endurecer si hace falta |
| `organization-media` | Privado (existente) | Logo/cover; docs org en subpaths o bucket hermano |
| `leover` | Público (existente) | **Deprecar escritura abierta**; migrar media social a bucket tipado o policies por path |
| `public-media` (candidato) | Público read / write acotada | Pets, posts, adoptions, LF con namespace `{owner}/{resource}/…` |
| `organization-documents` (candidato) | Privado | Verificación / docs institucionales |
| `moderation-evidence` (candidato) | Privado sensible | Evidencia M04; signed corta; sin listado |
| `support-attachments` (candidato) | Privado sensible | Tickets; PRIVACY/SAFETY más restringido |

**No** usar `leover` para evidencia, verificación ni soporte.

---

## 13. Propuesta de rutas

Concepto (alineado a spec M05):

```text
users/{userId}/avatars/{assetId}/{safeFilename}
users/{userId}/pets/{petId}/{assetId}/{safeFilename}
posts/{postId}/{assetId}/{safeFilename}
organizations/{orgId}/logo/{assetId}/{safeFilename}
organizations/{orgId}/verification/{assetId}/{safeFilename}
moderation/cases/{caseId}/evidence/{assetId}/{safeFilename}
support/tickets/{ticketId}/{assetId}/{safeFilename}
```

Reglas: sin `..`; un segmento de filename; UUID donde corresponda; ownership derivado de path **y** RLS/RPC; nombre original no es el path.

---

## 14. Permisos y ownership

| Contexto | Autoridad |
|----------|-----------|
| Usuario propio | `auth.uid()` = owner |
| Org | M03 `has_org_permission` |
| Staff evidencia/soporte | M02 `moderation.*` / `support.*` / `view_sensitive` |
| Público | Solo assets `visibility=PUBLIC` aprobados |
| AccountType / modules | **Nunca** |
| Roles M03 | Solo recursos de **su** organización |

UI no sustituye RLS. Cliente no elige bucket sensible: mapping purpose → bucket en servidor/contrato.

---

## 15. Validaciones y límites (propuesta)

| Control | Propuesta inicial |
|---------|-------------------|
| Imágenes sociales | ≤ 5–8 MiB; jpeg/png/webp; dimensión máx. configurable |
| Avatar/logo | ≤ 5 MiB (ya en bucket) |
| Documentos | PDF (+ allowlist); sin ejecutables; sin macros interpretadas |
| Evidencia | Imagen/PDF; bucket privado; TTL signed corto |
| Cliente | Validar size/MIME antes de upload; server vuelve a validar |
| Nombre | Sanitizar; no usar original como path |
| Cantidad | 1 primario + N galería por recurso (definir por dominio) |

---

## 16. Retención, eliminación y huérfanos

Hoy: **sin** jobs, **sin** soft-delete de objects, **sin** cascade a Storage.

Propuesta:

```text
UNLINK → SOFT_DELETE → PHYSICAL_DELETE (si 0 refs y retención vencida)
LEGAL_HOLD / RETENTION bloquean physical delete
```

- Evidencia M04: retención explícita.  
- Delete masivo **nunca** desde Android.  
- Jobs idempotentes; auditoría de deletes sensibles.

---

## 17. Archivos a crear/modificar (plan futuro; **no en Etapa 1**)

### Etapa 2 — Contratos

- `domain/files/*` (FileAsset, Visibility, Purpose, validators)  
- Contratos de upload session / signed URL  
- Tests unitarios  
- **Sin** SQL

### Etapa 3 — Persistencia

- Migración consecutiva (**no** editar 001–023 aplicadas)  
- Endurecer `leover` o introducir buckets tipados  
- Tablas `file_assets` / links  
- RLS + RPC; seeds si aplican  

### Etapa 4 — Android operativo

- Coordinador de upload; resolver signed/public  
- Document picker; límites; cleanup logout  
- Integrar M04 evidencia física si se aprueba bucket  

### Etapa 5 — Calidad / staging

- Checklist remoto; cierre M05  

---

## 18. Decisiones que requieren aprobación

1. ¿Deprecar escritura abierta de `leover` in-place o crear `public-media` y migrar?  
2. ¿Logo/cover org: signed URL pública temporal vs bucket/variante pública?  
3. ¿Evidencia M04 física en M05 Etapa 3 o fase posterior?  
4. ¿Unificar todas las `*_url` legacy hacia `file_asset_id` en una migración o gradual?  
5. ¿Antivirus/quarantine en alcance M05 o deuda explícita?  
6. ¿Thumbnails/variantes: cliente, Edge Function, o diferir?  
7. ¿TTL por defecto de signed URLs (1h vs minutos para sensibles)?  
8. ¿Mock Storage obligatorio cuando `useSupabase=false`?  

---

## 19. Plan por etapas (resumen)

| Etapa | Entrega | Bloqueos |
|-------|---------|----------|
| **1 (esta)** | Auditoría | — |
| 2 | Dominio + validadores (sin SQL) | Aprobación §18 |
| 3 | SQL/RLS/buckets tipados | Preferible staging 014–023 |
| 4 | UI/servicios operativos | — |
| 5 | Staging M05 + cierre | Sin producción |

---

## 20. Checklist Etapa 1

- [x] Working tree / rama desde commit base M04  
- [x] Inventario buckets `leover` / `profile-avatars` / `organization-media`  
- [x] Inventario policies y columnas path/URL  
- [x] Inventario Android Storage + pickers + DataProvider  
- [x] Mapa de usos clasificado  
- [x] Confirmaciones: sin service role; sin AccountType como auth archivo; refs M04 lógicas; sin base64; signed no persistidas (avatars/org)  
- [x] Riesgos documentados (incl. `leover` crítico)  
- [x] Propuesta modelo / buckets / rutas / retención  
- [x] Build / tests / lint ejecutados sin cambiar funcionalidad  
- [x] Staging remoto declarado pendiente  
- [x] Sin migraciones / buckets / RLS / pantallas / M06  

---

## 21. Parada

**No** se inicia Etapa 2 ni M06.  
**No** merge a `main`.  
**No** se modificó funcionalidad (solo este documento + preservación de la spec M05 en working tree).

Entregable: este documento.
