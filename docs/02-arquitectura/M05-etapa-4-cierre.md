# M05 — Cierre Etapa 4: UI y flujos operativos de archivos

**Fecha:** 2026-07-16  
**Rama:** `m05/etapa-4-ui-flujos-operativos-archivos`  
**Módulo:** M05 — Archivos, Media y Documentos  
**Estado de entrada:** Etapa 3 consolidada (`6b8ee5d2daefc3ad4ed2b07a3af1a4e26391943b`)  
**Producto:** LeoVer

---

## 1. Rama y commits

| Ref | SHA / nota |
|-----|------------|
| Commit base Etapa 3 | `6b8ee5d2daefc3ad4ed2b07a3af1a4e26391943b` |
| Rama de trabajo | `m05/etapa-4-ui-flujos-operativos-archivos` |
| Merge a `main` | **No** |
| Etapa 5 / M06 | **No** iniciados |
| Producción / staging | **No** usados; sin aplicar migraciones |
| Migración correctiva `025` | **No creada** — no se detectó defecto bloqueante en `024` |
| Corrección auth / username | **No incluida** (fuera de esta etapa por decisión explícita) |

---

## 2. Objetivo cumplido

La UI Android quedó conectada a los contratos y repositorios M05 de Etapas 2–3:
selección tipada de archivos, validación local + servidor, sesiones de upload con
progreso/cancelación/reintento, resolución pública y firmada temporal, reemplazo
seguro, unlink/delete y limpieza sensible al logout.

---

## 3. Componentes nuevos

### Capa de coordinación (`data/files/`)

| Archivo | Rol |
|---------|-----|
| `FileUploadCoordinator.kt` | Orquesta validar → preparar sesión → subir bytes → progreso → completar; lock anti doble envío; cancelación idempotente; retry; `safeReplace` (nunca borra el anterior antes de que el nuevo esté READY); unlink/requestDelete; `clearAllSensitiveState()` |
| `FileObjectUploader.kt` | Interface + `MockFileObjectUploader` / `SupabaseFileObjectUploader`; **rechaza `leover`** (`LEGACY_BUCKET_DENIED`), `content://`, `data:` |
| `FileLocalMetadataReader.kt` | Lectura de metadatos (nombre/MIME/tamaño) vía ContentResolver + fakes de test; `FileBytesReader` análogo |
| `FileDisplayResolver.kt` | Resolución para mostrar: assetId → pública/firmada temporal **en memoria**; expiración; re-chequeo de permisos en deep links sensibles; deny por organización incorrecta; fallback legacy solo lectura; rechaza `content://`/`data:`/base64 como referencia permanente |

### Dominio (`domain/files/`)

| Archivo | Rol |
|---------|-----|
| `FileAsset.kt` (ampliado) | `PreparedFileUpload`, `FileLocalMetadata`, `FileUploadPhase`, `FileUploadUiState` |
| `FileUiErrorMapper.kt` | Mensajes seguros en español; detecta backend sin migración `024` (`PGRST202`, "schema cache", función inexistente) → error **recuperable** "El servicio de archivos no está disponible todavía…"; nunca expone bucket/path/token/SQL/stack |

### UI (`ui/files/`)

| Archivo | Rol |
|---------|-----|
| `FilePickerLaunchers.kt` | Photo Picker (`PickVisualMedia` ImageOnly), multi-imagen, y `OpenDocument` PDF+imagen para documentos/evidencia/soporte |
| `FileUploadProgressSection.kt` | Barra de progreso + cancelar + reintentar + mensaje seguro |

### Limpieza de sesión (`viewmodel/files/`)

| Archivo | Rol |
|---------|-----|
| `FileSessionCleanup.kt` | Limpia coordinador, URIs de preview, signed URLs en memoria y locks; enganchado a `AdministrativeSessionCleanup.clear()` → se ejecuta en el logout existente sin tocar auth |

---

## 4. Repositorios ampliados

| Archivo | Cambio |
|---------|--------|
| `FileRepositories.kt` | `prepareUploadSession(...)` → `PreparedFileUpload` (mock: path vía `FilePathBuilder`; rechaza bucket legacy) |
| `SupabaseFileUploadRepository.kt` | `prepareUploadSession` parsea `session + version.storage_bucket/storage_path` del RPC `create_file_upload_session`; **rechaza** respuesta con bucket `leover` |
| `DataProvider.kt` | Expone `fileObjectUploader`, `fileLocalMetadataReader`, `fileBytesReader`, `fileUploadCoordinator`, `fileDisplayResolver`; mocks con `useSupabase=false`, Supabase con `true` |

---

## 5. Integración gradual por flujo

| Flujo | Purpose | Estado |
|-------|---------|--------|
| Avatar usuario (`EditProfileViewModel`, `ProfileOnboardingViewModel`) | `USER_AVATAR` | Coordinator M05; sin `content://` final; mock funcional |
| Logo / portada organización (`OrganizationViewModels`) | `ORGANIZATION_LOGO` / `ORGANIZATION_COVER` | Coordinator M05 + método para `ORGANIZATION_DOCUMENT` / verificación |
| Mascotas (`PetFormViewModel`) | `PET_AVATAR` | Coordinator M05; mock ya no persiste `content://` |
| Publicaciones / adopciones / perdidos-encontrados (`PublishViewModel`) | `POST_MEDIA` / `ADOPTION_MEDIA` / `LOST_FOUND_MEDIA` | Coordinator M05; nuevas subidas **no** usan `leover` |
| Evidencia moderación (`ModerationCaseViewModels` + pantalla) | `MODERATION_EVIDENCE` | Attach con visibilidad `AUTHORIZED_STAFF`; gate por permisos M04; solo assetId lógico |
| Adjuntos soporte (usuario y admin + pantallas) | `SUPPORT_ATTACHMENT` | Requester: `RESOURCE_PARTICIPANTS`; admin INTERNAL exige `support.view_sensitive`; los mensajes INTERNAL no llegan al solicitante (proyección server-side M04 intacta) |

Columnas `*_url` legacy siguen leyéndose (fallback `FileDisplayResolver` / `LegacyFileReferenceAdapter`); **sin migración masiva**; objetos legacy intactos.

---

## 6. Reglas de seguridad cumplidas

- `leover` prohibido para nuevos uploads (validado en uploader, repos y coordinator).  
- Sin persistir signed URLs (solo memoria con expiración; limpiadas al logout).  
- Sin `content://` como referencia final.  
- Android no elige bucket/path sensible (el servidor construye el path; cliente rechazado si difiere).  
- Errores de usuario sin bucket/path/token/SQL/stack (`FileUiErrorMapper`).  
- Sin fallback silencioso a Storage inseguro: si M05 falla, error recuperable visible.  
- Backend sin `024` → mensaje recuperable, no crash ni degradación insegura.  
- Sensibles nunca `PUBLIC` (dominio + constraint SQL Etapa 3).  
- Deep links sensibles re-verifican permisos (`FileDisplayResolver`).  
- Logout limpia URI, sesiones, previews y signed URLs (`FileSessionCleanup` vía `AdministrativeSessionCleanup`).  
- `useSupabase=false` → mocks M05 completos; `true` → repos Supabase M05.

---

## 7. Fuera de alcance (respetado)

- Error “No se pudo verificar el usuario. Intentá de nuevo” — **no tocado**.  
- Validación de username, `AuthRepository`, autenticación, migraciones M01/M02 — **sin cambios** (diff vacío verificado + `M05Etapa4AuthSurfaceUntouchedTest`).  
- Sin migración `025`; sin staging/producción; sin Etapa 5; sin M06; sin merge a `main`.

---

## 8. Pruebas y calidad

| Control | Resultado |
|---------|-----------|
| Doc calidad | `docs/04-calidad/M05-pruebas-ui-flujos-operativos-archivos.md` |
| Suite unitaria | **353** tests, **0** failures, **0** errors (338 conservadas + 15 nuevas) |
| `assembleDebug` | **SUCCESS** |
| `testDebugUnitTest` | **SUCCESS** |
| `lintDebug` | **SUCCESS** |

---

## 9. Deuda pendiente

| Ítem | Estado |
|------|--------|
| Staging `014`–`024` | **PENDIENTE DE VALIDACIÓN REMOTA** |
| Galería multi-imagen de mascotas (UI completa) | Parcial (picker disponible; integración plena Etapa 5+) |
| Migración gradual `*_url` → assetId | Continúa gradual |
| Error de verificación de usuario | Tarea separada (no M05) |
| Procesamiento imagen / antivirus | No afirmado |

---

## 10. Parada

Etapa 4 **cerrada a nivel local**. Cambios **sin commit** (a la espera de revisión).

**No** iniciar Etapa 5. **No** iniciar M06. **No** merge a `main`. **No** aplicar migraciones remotas.
