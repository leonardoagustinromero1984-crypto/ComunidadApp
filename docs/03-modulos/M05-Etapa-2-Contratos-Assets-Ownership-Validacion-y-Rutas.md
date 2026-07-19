# LEOVER — M05 Etapa 2: Contratos de Assets, Ownership, Validación y Rutas

**Módulo:** M05 — Archivos, Media y Documentos  
**Etapa:** 2 — Contratos de dominio, validadores, rutas y repositorios  
**Estado de entrada:** Etapa 1 aprobada y consolidada  
**Commit base:** `39aa369d8e5e45f41cebe6b769f6994d12a255e4`  
**Rama base:** `m05/archivos-media-documentos-auditoria`  
**Backend oficial:** Supabase  
**Calidad de entrada:** 261 tests, 0 failures, 0 errors; build y lint aprobados  
**Staging heredado:** migraciones `014`–`023` pendientes de validación remota  
**Alcance:** Kotlin puro, contratos, validadores, políticas de dominio, rutas, interfaces, mocks y pruebas.  
**Prohibido:** SQL, migraciones, buckets, policies, RLS, RPC, pantallas, navegación, producción y M06.

---

## 1. Objetivo

Definir una capa de archivos segura y reutilizable antes de modificar Storage.

La Etapa 2 debe entregar:

- modelo de asset y versiones;
- propósito y visibilidad;
- ownership de usuario u organización;
- referencias a recursos;
- estados de upload y procesamiento;
- rutas tipadas;
- validación de nombre, extensión, tamaño, MIME y cantidad;
- reglas de signed URLs;
- contratos de retención, unlink y delete;
- contratos de upload/download;
- repositorios dedicados;
- mocks deterministas;
- compatibilidad con Storage legacy;
- pruebas unitarias completas.

No debe corregir todavía el bucket `leover`. El hardening real se realizará en M05 Etapa 3 mediante una migración posterior a `023`.

---

## 2. Documentos obligatorios

Leer en este orden:

1. `/docs/01-producto/D01-Modulos-y-Orden.md`
2. `/docs/02-arquitectura/M00-cierre-final.md`
3. `/docs/02-arquitectura/M01-cierre-final.md`
4. `/docs/02-arquitectura/M02-cierre-final.md`
5. `/docs/02-arquitectura/M03-cierre-final.md`
6. `/docs/02-arquitectura/M04-cierre-final.md`
7. `/docs/02-arquitectura/M05-auditoria-inicial.md`
8. `/docs/03-modulos/M05-Archivos-Media-y-Documentos.md`
9. ADR-0001 a ADR-0005
10. Este documento.

---

## 3. Protección Git

Antes de trabajar:

1. Confirmar commit base:

```text
39aa369d8e5e45f41cebe6b769f6994d12a255e4
```

2. Confirmar working tree limpio.
3. Crear rama:

```text
m05/etapa-2-contratos-assets-ownership-validacion-rutas
```

4. No incorporar `wip/gps-mapas-pagos`.
5. No hacer merge a `main`.
6. No iniciar M06.
7. No aplicar staging ni producción.

---

## 4. Decisiones aprobadas

### 4.1 Estrategia para `leover`

- Mantener compatibilidad de lectura con URLs legacy.
- Considerar `leover` como **legacy y deprecable**.
- No agregar nuevos propósitos sensibles al bucket.
- Los contratos nuevos deben modelar paths y assets, no URL pública como identidad.
- La Etapa 3 decidirá entre:
  - endurecer `leover` por namespaces y ownership; o
  - introducir `public-media` y migrar gradualmente.
- No romper datos existentes desde Etapa 2.

### 4.2 Modelo de assets

- El archivo físico se separa de:
  - metadatos;
  - ownership;
  - vínculo con recurso;
  - URL de acceso.
- La URL firmada nunca es identidad permanente.
- Los dominios futuros deben referenciar `assetId` o referencia tipada.
- No almacenar base64 en modelos ni repositorios.

### 4.3 Ownership

Un asset tiene una única autoridad primaria:

```text
USER
ORGANIZATION
PLATFORM
```

Reglas:

- USER requiere `ownerUserId`;
- ORGANIZATION requiere `ownerOrganizationId`;
- PLATFORM se reserva para activos administrativos controlados;
- no permitir ownership dual;
- el recurso vinculado no reemplaza ownership;
- roles M03 solo autorizan assets de su organización;
- AccountType y `active_modules` no conceden acceso.

### 4.4 Evidencia y documentos sensibles

Los contratos deben incluir:

- `MODERATION_EVIDENCE`;
- `ORGANIZATION_VERIFICATION_DOCUMENT`;
- `SUPPORT_ATTACHMENT`.

Pero:

- sin bucket;
- sin upload real;
- sin URL permanente;
- sin Storage físico en Etapa 2.

### 4.5 Procesamiento

Definir contratos de procesamiento, pero no afirmar implementación.

Estados:

```text
NOT_REQUIRED
PENDING
PROCESSING
READY
REJECTED
FAILED
QUARANTINED
```

Compresión, resize, thumbnails, EXIF y scanning quedan como capacidades futuras.

### 4.6 Mock Storage

M05 Etapa 2 debe incluir mocks deterministas para `useSupabase = false`.

Los mocks:

- no usan `content://` como URL final;
- no simulan seguridad server-side como PASS automático;
- permiten probar upload, rechazo, progreso, cancelación y signed URL;
- no hacen red ni acceso a disco real.

### 4.7 Migración de `*_url`

Será gradual.

- No migrar todas las columnas legacy en Etapa 2.
- Definir adaptadores y contratos de compatibilidad.
- No tratar URL pública legacy como asset seguro.
- Registrar deuda para la Etapa 3 y siguientes.

---

## 5. Paquetes y archivos esperados

Adaptar nombres a la estructura real sin renombrar paquetes existentes.

### Dominio

Crear bajo una ubicación equivalente a:

```text
app/src/main/java/.../domain/files/
```

Archivos candidatos:

```text
FileAsset.kt
FileAssetOwner.kt
FileAssetPurpose.kt
FileAssetVisibility.kt
FileAssetStatus.kt
FileAssetVersion.kt
FileAssetLink.kt
FileUploadRequest.kt
FileUploadSession.kt
FileAccessRequest.kt
FileRetentionPolicy.kt
FileValidation.kt
FilePath.kt
FileContracts.kt
```

### Autorización

```text
domain/files/authorization/
```

Archivos candidatos:

```text
FileAuthorization.kt
FileOwnershipRules.kt
FileAccessDecision.kt
```

### Repositorios

```text
FileAssetRepository
FileUploadRepository
FileDownloadRepository
FileAccessRepository
FileRetentionRepository
```

Mocks/fakes:

```text
MockFileAssetRepository
MockFileUploadRepository
MockFileDownloadRepository
MockFileAccessRepository
MockFileRetentionRepository
```

No crear implementaciones Supabase.

---

## 6. Propósitos

Definir inicialmente:

```text
USER_AVATAR
USER_COVER
PET_AVATAR
PET_GALLERY
ORGANIZATION_LOGO
ORGANIZATION_COVER
ORGANIZATION_DOCUMENT
ORGANIZATION_VERIFICATION_DOCUMENT
POST_MEDIA
ADOPTION_MEDIA
LOST_FOUND_MEDIA
SERVICE_PROFILE_MEDIA
SUPPORT_ATTACHMENT
MODERATION_EVIDENCE
MESSAGE_ATTACHMENT
EVENT_MEDIA
PRODUCT_MEDIA
OTHER
```

Cada propósito debe declarar:

- clase de sensibilidad;
- extensiones permitidas;
- MIME permitidos;
- tamaño máximo;
- cantidad máxima;
- visibilidad admitida;
- ownership admitido;
- si requiere procesamiento;
- si admite URL pública;
- si exige retención;
- bucket lógico futuro;
- patrón de ruta.

No hardcodear reglas dispersas en ViewModels.

---

## 7. Visibilidad

Valores:

```text
PUBLIC
OWNER_ONLY
ORGANIZATION_PRIVATE
AUTHORIZED_STAFF
RESOURCE_PARTICIPANTS
SIGNED_LINK_ONLY
```

Reglas:

- `PUBLIC` solo para propósitos aprobados;
- documentos y evidencia nunca `PUBLIC`;
- `AUTHORIZED_STAFF` requiere permisos M02;
- `ORGANIZATION_PRIVATE` requiere permisos M03;
- `OWNER_ONLY` requiere actor propietario;
- `SIGNED_LINK_ONLY` no implica acceso sin autorización;
- error o contexto incompleto → deny.

---

## 8. Asset

Modelo mínimo:

```text
id
owner
purpose
visibility
status
currentVersionId?
createdByUserId
createdAt
updatedAt
deletedAt?
retentionUntil?
```

Reglas:

- ID no vacío;
- owner válido;
- purpose compatible con owner;
- visibility compatible con purpose;
- `deletedAt` solo con estado eliminado;
- `retentionUntil` no puede estar en el pasado al crear;
- asset sensible no admite URL pública;
- `currentVersionId` solo cuando existe versión lista;
- no incluir URL firmada persistente.

---

## 9. Versión

Modelo mínimo:

```text
id
assetId
storageBucket
storagePath
originalFilename
safeFilename
declaredMimeType
detectedMimeType?
sizeBytes
checksum?
status
createdAt
```

Reglas:

- path no vacío;
- bucket no arbitrario;
- filename sanitizado;
- `sizeBytes > 0`;
- detected MIME prevalece sobre declarado;
- mismatch MIME/extensión puede rechazar;
- checksum opcional en contrato, requerido para sensibles cuando se implemente;
- versión `READY` debe tener path válido;
- URL no forma parte de la versión;
- no permitir `..`, slash inversa o segmentos vacíos.

---

## 10. Links a recursos

Modelo:

```text
assetId
resourceType
resourceId
relationType
sortOrder?
isPrimary
createdAt
```

Tipos de recurso iniciales:

```text
USER
PET
ORGANIZATION
POST
ADOPTION
LOST_FOUND_CASE
SERVICE_PROFILE
MODERATION_CASE
ORGANIZATION_VERIFICATION
SUPPORT_TICKET
MESSAGE
EVENT
PRODUCT
OTHER
```

Reglas:

- un primario por recurso y relación;
- eliminar link no borra automáticamente asset;
- asset sensible solo se vincula a recursos compatibles;
- no vincular asset de organización A con recurso de organización B;
- un asset legacy puede representarse como referencia externa solo para lectura;
- no admitir URL como `resourceId`.

---

## 11. Upload request y sesión

### Request

```text
purpose
owner
resourceRef?
originalFilename
declaredMimeType?
sizeBytes
requestedVisibility
```

### Sesión

```text
id
assetId
versionId
state
progressPercent
createdAt
expiresAt?
failureCode?
```

Estados:

```text
CREATED
VALIDATING
READY_TO_UPLOAD
UPLOADING
UPLOADED
PROCESSING
COMPLETED
FAILED
CANCELLED
EXPIRED
```

Reglas:

- progreso entre 0 y 100;
- no pasar a COMPLETED sin versión READY;
- cancelación idempotente;
- sesión expirada no acepta upload;
- evitar doble submit;
- errores sin paths sensibles;
- upload de asset sensible requiere autorización adicional;
- mock debe usar reloj inyectable.

---

## 12. Validadores

### Nombre

- eliminar path;
- normalizar espacios;
- limitar longitud;
- bloquear `..`;
- bloquear control chars;
- bloquear doble extensión peligrosa;
- no usar nombre original como path;
- conservar extensión segura.

### Extensión

Allowlist por propósito.

Prohibir como mínimo:

```text
exe
apk
bat
cmd
com
msi
jar
js
html
htm
svg
sh
ps1
dll
scr
```

SVG puede revisarse en el futuro; por defecto no permitido.

### MIME

- allowlist por propósito;
- declarado es no confiable;
- detected MIME, cuando exista, tiene prioridad;
- mismatch peligroso → rechazo;
- no declarar todo como JPEG;
- tipos desconocidos → deny.

### Tamaño

Propuesta inicial de contrato:

| Propósito | Máximo |
|---|---:|
| Avatar/logo/cover | 5 MiB |
| Media social | 8 MiB |
| Evidencia imagen | 10 MiB |
| Documento PDF | 15 MiB |
| Otros | deny hasta configurar |

Los valores deben centralizarse.

### Cantidad

Definir límites por recurso:

- avatar/logo/cover: 1 primario;
- pet gallery: configurable;
- post: configurable;
- evidencia: configurable;
- soporte: configurable;
- unknown → deny.

### Imágenes

Contrato futuro para:

- dimensiones;
- orientación;
- EXIF;
- resize;
- thumbnails.

No implementar procesamiento real.

---

## 13. Rutas

Crear un builder tipado.

Ejemplos:

```text
users/{userId}/avatars/{assetId}/{safeFilename}
users/{userId}/pets/{petId}/{assetId}/{safeFilename}
posts/{postId}/{assetId}/{safeFilename}
organizations/{organizationId}/logo/{assetId}/{safeFilename}
organizations/{organizationId}/cover/{assetId}/{safeFilename}
organizations/{organizationId}/documents/{assetId}/{safeFilename}
organizations/{organizationId}/verification/{assetId}/{safeFilename}
moderation/cases/{caseId}/evidence/{assetId}/{safeFilename}
support/tickets/{ticketId}/{assetId}/{safeFilename}
```

Reglas:

- path generado, no arbitrario;
- segmentos validados;
- UUID o ID normalizado;
- ownership compatible;
- purpose compatible;
- sin traversal;
- sin URL;
- bucket lógico derivado del purpose;
- el cliente no elige bucket sensible;
- no usar filename original sin sanitizar.

Compatibilidad:

- `StoragePaths` existente debe reutilizarse o adaptarse;
- no crear múltiples builders incompatibles;
- paths legacy se reconocen solo mediante adaptador de lectura;
- no autorizar escritura nueva con path legacy abierto.

---

## 14. Bucket lógico

Definir códigos de dominio, no buckets físicos definitivos:

```text
PROFILE_AVATARS
ORGANIZATION_MEDIA
PUBLIC_MEDIA
ORGANIZATION_DOCUMENTS
MODERATION_EVIDENCE
SUPPORT_ATTACHMENTS
LEGACY_LEOVER_READ_ONLY
```

Reglas:

- resolver purpose → bucket lógico;
- `LEGACY_LEOVER_READ_ONLY` no acepta upload nuevo desde contratos M05;
- la asignación física se hará en Etapa 3;
- desconocido → deny;
- sensibles nunca resuelven a PUBLIC_MEDIA.

---

## 15. Signed URLs y acceso

Contrato:

```text
requestSignedAccess(assetId, actor, purpose, ttlClass)
```

Clases sugeridas:

```text
PUBLIC_RESOLUTION
STANDARD_PRIVATE
SENSITIVE_SHORT
```

Política inicial:

- `PUBLIC_RESOLUTION`: solo assets PUBLIC;
- `STANDARD_PRIVATE`: TTL configurable de hasta 60 minutos;
- `SENSITIVE_SHORT`: TTL corta, recomendada 5–10 minutos;
- no persistir URL;
- no loguear token/query;
- reautorizar al renovar;
- cambio de permisos impide nuevas URLs;
- URL vencida no es error de integridad del asset.

No hardcodear valores fuera de una política central.

---

## 16. Autorización

Resultado explícito:

```text
ALLOWED
DENIED_NOT_AUTHENTICATED
DENIED_NOT_OWNER
DENIED_MISSING_ORG_PERMISSION
DENIED_MISSING_PLATFORM_PERMISSION
DENIED_PURPOSE
DENIED_VISIBILITY
DENIED_RESOURCE_MISMATCH
DENIED_RETENTION
DENIED_INVALID_STATE
DENIED_UNKNOWN
```

Reglas:

- USER: actor = owner;
- ORGANIZATION: permiso M03 según propósito;
- PLATFORM/sensible: permiso M02;
- lectura pública solo asset PUBLIC y READY;
- escritura siempre autenticada;
- delete respeta referencias y retención;
- roles M03 solo actúan dentro de su organización;
- `AccountType` y `active_modules` no conceden acceso;
- error → deny.

---

## 17. Retención y eliminación

Acciones:

```text
UNLINK
SOFT_DELETE
PHYSICAL_DELETE
QUARANTINE
LEGAL_HOLD
RESTORE
```

Reglas:

- unlink no borra físico;
- physical delete requiere cero referencias activas;
- retención futura bloquea delete;
- legal hold bloquea delete;
- sensibles requieren auditoría;
- delete desde Android solo solicita operación;
- jobs futuros serán idempotentes;
- asset eliminado no genera signed URL;
- restore solo si el físico existe y la política lo permite.

---

## 18. Compatibilidad legacy

Definir:

```text
LegacyFileReference
LegacyPublicUrlReference
LegacyStoragePathReference
```

Reglas:

- lectura únicamente;
- nunca concede ownership;
- nunca habilita upload;
- URL pública no implica asset verificado;
- no generar nuevos valores legacy;
- adaptadores permiten migración gradual;
- documentar origen y riesgo;
- no intentar descargar durante validadores puros.

---

## 19. Repositorios

### FileAssetRepository

```text
createDraftAsset
getAsset
listAssetsForResource
linkAsset
unlinkAsset
markDeleted
restoreAsset
```

### FileUploadRepository

```text
createUploadSession
validateUpload
startUpload
updateProgress
completeUpload
failUpload
cancelUpload
```

### FileDownloadRepository

```text
requestAccess
resolvePublicAsset
requestSignedUrl
```

### FileAccessRepository

```text
canRead
canUpload
canReplace
canDelete
```

### FileRetentionRepository

```text
getRetentionPolicy
canPhysicallyDelete
requestLegalHold
releaseLegalHold
```

En Etapa 2:

- interfaces;
- mocks/fakes;
- `AppResult`;
- errores tipados;
- sin Supabase;
- sin lectura de bytes real.

---

## 20. Integración con DataProvider

Agregar los repositorios M05 mediante providers existentes.

Para `useSupabase = false`:

- usar mocks M05;
- no devolver `null`;
- IDs y paths deterministas;
- no persistir `content://`;
- permitir configurar éxitos y errores;
- limpiar sesiones al logout cuando corresponda.

Para `useSupabase = true`:

- mantener servicios existentes sin reemplazo en Etapa 2;
- no cablear implementaciones nuevas aún;
- no romper avatars, organization media ni media legacy.

---

## 21. Pruebas obligatorias

Crear pruebas equivalentes a:

```text
FileAssetRulesTest
FileAssetVersionRulesTest
FileOwnershipRulesTest
FilePurposePolicyTest
FileVisibilityRulesTest
FileValidationRulesTest
FileNameSanitizerTest
FilePathBuilderTest
FileUploadSessionRulesTest
FileSignedAccessRulesTest
FileRetentionRulesTest
FileLegacyCompatibilityTest
FileRepositoryMocksTest
```

Casos mínimos:

- owner dual;
- purpose/visibility incompatible;
- sensible público;
- extensión prohibida;
- MIME mismatch;
- tamaño excedido;
- nombre con traversal;
- path arbitrario;
- bucket sensible elegido por cliente;
- signed URL persistida;
- TTL sensible;
- delete con referencias;
- delete con retención;
- upload duplicado;
- cancelación idempotente;
- legacy read-only;
- unknown/error → deny.

Conservar las 261 pruebas existentes.

---

## 22. Calidad

Ejecutar:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Documentar:

- cantidad total de tests;
- archivos creados/modificados;
- build;
- lint;
- deuda;
- staging pendiente.

---

## 23. Documento de cierre

Crear exactamente:

```text
/docs/02-arquitectura/M05-etapa-2-cierre.md
```

Debe incluir:

- rama;
- commit base;
- archivos;
- contratos;
- decisiones;
- ownership;
- propósito;
- visibilidad;
- validación;
- rutas;
- signed URLs;
- retención;
- compatibilidad legacy;
- repositorios y mocks;
- pruebas;
- build/lint;
- riesgos;
- staging;
- checklist;
- parada.

---

## 24. Criterios de aceptación

- [ ] Working tree inicial limpio.
- [ ] Rama correcta.
- [ ] Sin SQL, migraciones, buckets, policies o RLS.
- [ ] Sin pantallas o navegación.
- [ ] Sin implementaciones Supabase nuevas.
- [ ] Modelo de asset y versión.
- [ ] Ownership único.
- [ ] Propósitos centralizados.
- [ ] Visibilidad centralizada.
- [ ] Validadores de nombre, extensión, MIME, tamaño y cantidad.
- [ ] Rutas tipadas.
- [ ] Bucket lógico derivado.
- [ ] `leover` legacy read-only en contratos nuevos.
- [ ] Signed URL no persistente.
- [ ] Retención y eliminación modeladas.
- [ ] Compatibilidad legacy de lectura.
- [ ] Repositorios dedicados.
- [ ] Mocks deterministas.
- [ ] DataProvider sin Storage null en mocks M05.
- [ ] AccountType/modules sin autoridad.
- [ ] Roles M03 limitados a su organización.
- [ ] Evidencia y documentos sensibles nunca públicos.
- [ ] Tests verdes.
- [ ] Build y lint verdes.
- [ ] Cierre creado.
- [ ] Sin Etapa 3.
- [ ] Sin M06.
- [ ] Sin merge a main.

---

## 25. Parada

No iniciar Etapa 3.

No corregir todavía `leover` mediante SQL o policies.

No crear buckets.

No hacer merge a `main`.

Detenerse al crear:

```text
/docs/02-arquitectura/M05-etapa-2-cierre.md
```
