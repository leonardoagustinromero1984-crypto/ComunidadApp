# LEOVER — M05 Etapa 3: Persistencia, Storage, RLS y RPC

**Módulo:** M05 — Archivos, Media y Documentos  
**Etapa:** 3 — Persistencia, buckets tipados, RLS, RPC e integración Supabase  
**Estado de entrada:** Etapa 2 aprobada y consolidada  
**Commit base:** `71210f788839e09f5eff99b440059152039c035c`  
**Rama base:** `m05/etapa-2-contratos-assets-ownership-validacion-rutas`  
**Calidad de entrada:** 327 tests, 0 failures, 0 errors; `assembleDebug` y `lintDebug` en SUCCESS  
**Backend oficial:** Supabase  
**Staging heredado:** migraciones `014`–`023` pendientes de validación remota  
**Alcance:** SQL, Storage, RLS, RPC, repositorios Supabase, adaptadores legacy y pruebas.  
**Prohibido:** pantallas nuevas, navegación, producción, M06, GPS/mapas/pagos y merge a `main`.

---

## 1. Objetivo

Implementar la base física y segura de M05:

- persistencia central de assets;
- versiones y vínculos;
- buckets tipados;
- ownership verificable;
- policies RLS de Storage;
- RPC server-side;
- URLs públicas o firmadas según visibilidad;
- sesiones de upload;
- retención y eliminación controlada;
- auditoría de accesos sensibles;
- repositorios Supabase;
- compatibilidad gradual con media legacy.

La prioridad inicial es eliminar la escritura abierta del bucket `leover` sin romper la lectura de URLs existentes.

---

## 2. Documentación obligatoria

Leer en este orden:

1. `/docs/01-producto/D01-Modulos-y-Orden.md`
2. `/docs/02-arquitectura/M00-cierre-final.md`
3. `/docs/02-arquitectura/M01-cierre-final.md`
4. `/docs/02-arquitectura/M02-cierre-final.md`
5. `/docs/02-arquitectura/M03-cierre-final.md`
6. `/docs/02-arquitectura/M04-cierre-final.md`
7. `/docs/02-arquitectura/M05-auditoria-inicial.md`
8. `/docs/02-arquitectura/M05-etapa-2-cierre.md`
9. `/docs/03-modulos/M05-Archivos-Media-y-Documentos.md`
10. `/docs/03-modulos/M05-Etapa-2-Contratos-Assets-Ownership-Validacion-y-Rutas.md`
11. ADR-0001 a ADR-0005
12. Este documento.

---

## 3. Protección Git

Antes de trabajar:

1. Confirmar commit base:

```text
71210f788839e09f5eff99b440059152039c035c
```

2. Confirmar working tree limpio.
3. Crear rama:

```text
m05/etapa-3-persistencia-storage-rls-rpc
```

4. No incorporar `wip/gps-mapas-pagos`.
5. No hacer merge a `main`.
6. No iniciar M06.
7. No usar producción.
8. No afirmar validación remota sin evidencia.

---

## 4. Decisiones obligatorias

### 4.1 Bucket `leover`

La migración de Etapa 3 debe:

- mantener lectura pública legacy;
- eliminar INSERT/UPDATE/DELETE abiertos para `authenticated`;
- no borrar objetos existentes;
- no cambiar URLs públicas existentes;
- impedir nuevos uploads M05 a `leover`;
- conservar adaptadores de lectura legacy;
- documentar que `leover` queda en modo **legacy read-only**.

No editar la migración `002`.

### 4.2 Nuevo bucket de media pública

Crear un bucket tipado:

```text
public-media
```

Características:

- lectura pública;
- escritura autenticada y limitada por ownership;
- solo imágenes permitidas;
- límite de tamaño configurado;
- rutas generadas por M05;
- sin path arbitrario;
- no usar para documentos, evidencia o soporte;
- no usar para URLs firmadas sensibles.

Propósitos iniciales:

```text
PET_AVATAR
PET_GALLERY
POST_MEDIA
ADOPTION_MEDIA
LOST_FOUND_MEDIA
SERVICE_PROFILE_MEDIA
EVENT_MEDIA
PRODUCT_MEDIA
```

### 4.3 Buckets privados

Crear:

```text
organization-documents
moderation-evidence
support-attachments
```

Reglas:

- privados;
- sin lectura pública;
- URL firmada temporal;
- policies por ownership y permisos;
- sin listado global;
- sin escritura directa sensible fuera de flujo autorizado;
- MIME y tamaño allowlisted;
- no almacenar URL firmada en SQL.

Mantener existentes:

```text
profile-avatars
organization-media
```

No recrearlos ni cambiar su propósito sin necesidad.

### 4.4 Asset como fuente de verdad

Los nuevos uploads M05 deben registrar:

- asset;
- versión;
- bucket;
- path;
- ownership;
- propósito;
- visibilidad;
- estado;
- tamaño;
- MIME;
- checksum cuando esté disponible;
- vínculo al recurso.

Las columnas `*_url` legacy siguen siendo compatibles, pero no son la fuente de verdad de nuevos flujos M05.

---

## 5. Migración

Crear una migración consecutiva posterior a `023`.

Nombre esperado:

```text
supabase/migrations/024_m05_file_assets_storage_foundation.sql
```

No editar `001`–`023`.

La migración debe ser idempotente donde corresponda y segura frente a datos legacy.

---

## 6. Tablas

### 6.1 `file_assets`

Campos mínimos:

```text
id uuid primary key
owner_kind text
owner_user_id uuid null
owner_organization_id uuid null
purpose text
visibility text
status text
current_version_id uuid null
created_by uuid
created_at timestamptz
updated_at timestamptz
deleted_at timestamptz null
retention_until timestamptz null
legal_hold boolean
metadata jsonb
```

Constraints:

- ownership válido y único;
- USER requiere `owner_user_id`;
- ORGANIZATION requiere `owner_organization_id`;
- PLATFORM no admite owner user/org;
- propósito allowlisted;
- visibilidad allowlisted;
- sensibles nunca PUBLIC;
- `deleted_at` coherente con estado;
- legal hold bloquea eliminación;
- actor y owner no se aceptan ciegamente desde cliente.

### 6.2 `file_asset_versions`

Campos mínimos:

```text
id uuid primary key
asset_id uuid
storage_bucket text
storage_path text
original_filename text
safe_filename text
declared_mime_type text
detected_mime_type text null
size_bytes bigint
checksum text null
status text
created_by uuid
created_at timestamptz
```

Constraints:

- path único por bucket;
- tamaño positivo;
- bucket allowlisted;
- path sin URL;
- path sin traversal;
- estado allowlisted;
- detected MIME prevalece;
- URLs firmadas no se almacenan.

### 6.3 `file_asset_links`

Campos:

```text
id uuid primary key
asset_id uuid
resource_type text
resource_id text
relation_type text
sort_order integer null
is_primary boolean
created_at timestamptz
created_by uuid
deleted_at timestamptz null
```

Reglas:

- un primario activo por recurso/relación;
- unlink lógico;
- asset sensible solo con recurso compatible;
- resource ID no puede ser URL.

### 6.4 `file_upload_sessions`

Campos:

```text
id uuid primary key
asset_id uuid
version_id uuid
state text
progress_percent integer
failure_code text null
expires_at timestamptz
created_by uuid
created_at timestamptz
updated_at timestamptz
```

Reglas:

- progreso 0–100;
- sesión expirada no completa;
- cancelación idempotente;
- una sesión activa por versión;
- no usar progreso como única prueba de upload completo.

### 6.5 `file_access_audit`

Campos mínimos:

```text
id uuid primary key
asset_id uuid
actor_user_id uuid
action text
result text
context_type text null
context_id text null
created_at timestamptz
metadata jsonb
```

Uso:

- accesos sensibles;
- signed URL;
- download;
- delete;
- legal hold;
- restore;
- sin tokens, query strings o contenido.

### 6.6 Retención

Puede resolverse mediante tabla:

```text
file_retention_policies
```

o configuración server-side central.

Debe existir una fuente única por propósito.

---

## 7. Índices y constraints

Crear índices para:

- owner user;
- owner organization;
- purpose;
- status;
- asset links por recurso;
- versiones por asset;
- sesiones activas;
- auditoría por asset/actor/fecha;
- assets con `deleted_at`;
- assets con `retention_until`.

Prevenir:

- ownership dual;
- path duplicado;
- doble primario;
- doble sesión activa;
- signed URL persistida;
- recurso URL;
- bucket no allowlisted.

---

## 8. Storage buckets

### `public-media`

Propuesta inicial:

```text
public = true
file_size_limit = 8 MiB
allowed_mime_types = image/jpeg, image/png, image/webp
```

### `organization-documents`

```text
public = false
file_size_limit = 15 MiB
allowed_mime_types = application/pdf, image/jpeg, image/png, image/webp
```

### `moderation-evidence`

```text
public = false
file_size_limit = 15 MiB
allowed_mime_types = application/pdf, image/jpeg, image/png, image/webp
```

### `support-attachments`

```text
public = false
file_size_limit = 15 MiB
allowed_mime_types = application/pdf, image/jpeg, image/png, image/webp
```

No incluir ejecutables, HTML, SVG, APK, ZIP o DOCX en esta primera base física salvo aprobación explícita posterior.

---

## 9. Paths físicos

Usar rutas generadas:

```text
users/{userId}/pets/{petId}/{assetId}/{safeFilename}
posts/{postId}/{assetId}/{safeFilename}
adoptions/{adoptionId}/{assetId}/{safeFilename}
lost-found/{caseId}/{assetId}/{safeFilename}
services/{serviceProfileId}/{assetId}/{safeFilename}
events/{eventId}/{assetId}/{safeFilename}
products/{productId}/{assetId}/{safeFilename}

organizations/{organizationId}/documents/{assetId}/{safeFilename}
organizations/{organizationId}/verification/{assetId}/{safeFilename}

moderation/cases/{caseId}/evidence/{assetId}/{safeFilename}
support/tickets/{ticketId}/{assetId}/{safeFilename}
```

Reglas:

- path definido desde purpose y recurso;
- no aceptar path completo desde Android;
- validar cada segmento;
- asset ID incluido;
- filename sanitizado;
- ownership y recurso verificados por RPC/RLS;
- bucket sensible derivado, nunca elegido por cliente.

---

## 10. RLS de tablas

### Lectura

Permitir según:

- owner usuario;
- miembro autorizado de organización;
- staff M02 con permiso;
- participante de recurso cuando aplique;
- asset público READY y no eliminado;
- signed access generado mediante RPC autorizada.

### Escritura

No permitir INSERT/UPDATE/DELETE directo sensible desde Android.

Preferir RPC para:

- crear draft;
- iniciar upload;
- completar versión;
- vincular;
- reemplazar;
- solicitar delete;
- legal hold;
- restore.

### Deny-by-default

- error de permiso;
- sesión ausente;
- owner inválido;
- recurso inexistente;
- purpose desconocido;
- asset eliminado;
- retención activa;
- legal hold;
- estado inválido.

---

## 11. Policies de `storage.objects`

### `leover`

- mantener SELECT público;
- eliminar policies de INSERT/UPDATE/DELETE abiertas;
- no crear nuevas escrituras.

### `public-media`

SELECT:

- público para objetos registrados como asset PUBLIC/READY;
- evaluar si policy consulta `file_asset_versions` de forma segura.

INSERT/UPDATE/DELETE:

- solo actor autorizado;
- path esperado;
- owner/recurso correcto;
- purpose compatible;
- sesión de upload válida;
- no sobrescribir asset ajeno.

### Buckets privados

SELECT:

- evitar SELECT directo amplio;
- preferir signed URLs mediante RPC;
- si existe policy, acotarla a owner/staff/organización.

INSERT/UPDATE/DELETE:

- owner o permiso correspondiente;
- path y asset registrados;
- upload session válida;
- sensibles con permisos M02 específicos.

Evitar RLS recursiva.  
Si se usan helpers `SECURITY DEFINER`, fijar `search_path = public`.

---

## 12. Permisos

Reutilizar M02 y M03.

Permisos globales candidatos para assets sensibles:

```text
files.view_sensitive
files.manage_sensitive
files.audit
```

Antes de crear nuevos códigos:

- verificar si `moderation.view_sensitive`;
- `support.view_sensitive`;
- `organizations.review_verification`;
- `audit.view`

cubren los casos.

No crear rol nuevo si alcanza la matriz existente.

M03:

- `organization.update` para logo/cover;
- permiso específico futuro para documentos si se justifica;
- miembros solo en su organización.

---

## 13. RPC

Crear RPC equivalentes a:

### Assets

```text
create_file_asset_draft
get_file_asset
list_file_assets_for_resource
link_file_asset
unlink_file_asset
request_file_asset_delete
restore_file_asset
```

### Upload

```text
create_file_upload_session
complete_file_upload
fail_file_upload
cancel_file_upload
```

### Acceso

```text
resolve_public_file_asset
request_file_signed_url
```

### Retención

```text
place_file_legal_hold
release_file_legal_hold
can_physically_delete_file_asset
```

### Auditoría

```text
list_file_access_audit
```

Reglas:

- actor desde `auth.uid()`;
- `SECURITY DEFINER`;
- `search_path = public`;
- no aceptar owner arbitrario;
- no devolver signed URL sin autorización;
- TTL server-side;
- sensibles con TTL corto;
- auditoría en la misma transacción cuando corresponda;
- errores tipados y seguros.

---

## 14. Signed URLs

Implementar mediante repositorio/servicio Supabase:

- no persistir;
- TTL definido server-side;
- `STANDARD_PRIVATE`: máximo 60 minutos;
- `SENSITIVE_SHORT`: máximo 10 minutos;
- recomendación sensible: 5 minutos;
- renovación reautoriza;
- no loguear URL completa;
- asset eliminado, rechazado o en hold no se firma salvo permiso específico.

Para `public-media`, resolver URL pública solo para asset PUBLIC/READY.

---

## 15. Integración Android

Crear implementaciones Supabase:

```text
SupabaseFileAssetRepository
SupabaseFileUploadRepository
SupabaseFileDownloadRepository
SupabaseFileAccessRepository
SupabaseFileRetentionRepository
```

Reutilizar:

- cliente Supabase existente;
- `AppResult`;
- `AppError`;
- `AppLogger`;
- `DataProvider`;
- contratos M05;
- `StoragePaths` para lectura legacy;
- servicios avatar/org existentes.

No crear otro cliente Supabase.

### DataProvider

- `useSupabase = true` → repos M05 Supabase;
- `useSupabase = false` → mocks M05;
- no devolver null;
- no romper Storage legacy.

---

## 16. Compatibilidad legacy

### Lectura

Mantener:

- URLs de `leover`;
- columnas `*_url`;
- paths existentes de avatars y organizaciones.

### Nuevas escrituras

No usar `leover`.

Los nuevos flujos M05 deben usar:

- `public-media`;
- buckets privados tipados;
- `file_assets`.

### Adaptadores

Crear:

```text
LegacyFileReferenceAdapter
FileAssetReferenceResolver
```

Reglas:

- legacy no concede ownership;
- no convertir automáticamente una URL arbitraria en asset confiable;
- no borrar objetos legacy;
- no migrar masivamente en esta etapa;
- documentar estrategia gradual.

---

## 17. Integración M04

M04 usa referencias lógicas.

En Etapa 3 se permite habilitar persistencia física base para:

- `MODERATION_EVIDENCE`;
- `ORGANIZATION_VERIFICATION_DOCUMENT`;
- `SUPPORT_ATTACHMENT`.

Sin crear UI nueva.

Los repositorios M04 pueden recibir `assetId` o link seguro, pero:

- no cambiar decisiones de moderación;
- no exponer URL permanente;
- no permitir que requester vea INTERNAL;
- respetar permisos sensibles;
- documentar integración.

---

## 18. Validación y seguridad

El servidor debe validar nuevamente:

- purpose;
- owner;
- bucket;
- path;
- estado;
- MIME;
- tamaño;
- visibilidad;
- recurso;
- permisos;
- retención.

No confiar en validación Android.

No afirmar:

- antivirus;
- MIME sniffing profundo;
- EXIF strip;
- thumbnails;
- quarantine automática,

si no se implementan.

`QUARANTINED` puede existir como estado reservado sin pipeline automático.

---

## 19. Eliminación

Flujo:

```text
UNLINK
→ SOFT_DELETE
→ PHYSICAL_DELETE cuando no hay links, retención ni legal hold
```

Reglas:

- Android no borra físicamente de forma masiva;
- delete request server-side;
- bucket object y metadatos deben mantenerse consistentes;
- fallo parcial debe ser recuperable;
- auditoría para sensibles;
- jobs automáticos pueden quedar diferidos si se documenta.

No eliminar archivos legacy en esta etapa.

---

## 20. Pruebas SQL

Crear:

```text
/docs/04-calidad/M05-pruebas-persistencia-storage-rls-rpc.md
```

Cubrir como mínimo:

### `leover`

- lectura pública sigue;
- INSERT authenticated directo denegado;
- UPDATE directo denegado;
- DELETE directo denegado;
- URL legacy no cambia.

### `public-media`

- usuario no escribe path ajeno;
- path arbitrario denegado;
- MIME/tamaño inválido denegado;
- lectura pública solo READY/PUBLIC;
- asset eliminado no resuelve.

### Organización

- miembro sin permiso no sube documento;
- miembro autorizado sí;
- org A no accede org B;
- verificación requiere reviewer autorizado.

### Moderación y soporte

- evidencia no pública;
- signed URL solo permiso;
- TTL sensible;
- requester no accede evidencia interna;
- soporte PRIVACY/SAFETY requiere permiso sensible.

### Assets

- ownership dual bloqueado;
- asset sensible PUBLIC bloqueado;
- doble primario bloqueado;
- doble sesión activa bloqueada;
- signed URL no persistida;
- delete bloqueado por referencias;
- legal hold y retención bloquean delete.

### Auditoría

- escritura cliente denegada;
- eventos sensibles registrados;
- no se registran tokens.

---

## 21. Pruebas Android

Agregar pruebas equivalentes a:

```text
SupabaseFileAssetRepositoryTest
SupabaseFileUploadRepositoryTest
SupabaseFileDownloadRepositoryTest
SupabaseFileAccessRepositoryTest
SupabaseFileRetentionRepositoryTest
FileLegacyReferenceAdapterTest
M05PermissionMatrixTest
M05StoragePathSecurityTest
M05DataProviderWiringTest
```

Conservar las 327 pruebas existentes.

No depender de staging para unit tests.

---

## 22. Calidad local

Ejecutar:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Requisitos:

- build SUCCESS;
- todos los tests aprobados;
- lint con 0 errores;
- no eliminar pruebas;
- no crear baseline nuevo;
- documentar total final.

---

## 23. Documentos de salida

Crear exactamente:

```text
/docs/02-arquitectura/M05-etapa-3-cierre.md
/docs/04-calidad/M05-pruebas-persistencia-storage-rls-rpc.md
```

El cierre debe incluir:

- rama y commit base;
- migración;
- buckets;
- hardening de `leover`;
- tablas;
- RLS;
- RPC;
- permisos;
- repositorios;
- DataProvider;
- compatibilidad legacy;
- integración M04;
- pruebas;
- build/lint;
- staging;
- riesgos;
- checklist;
- parada.

---

## 24. Criterios de aceptación

- [ ] Working tree inicial limpio.
- [ ] Rama correcta.
- [ ] Migración `024` consecutiva.
- [ ] Sin editar `001`–`023`.
- [ ] `leover` queda read-only para nuevas escrituras.
- [ ] URLs legacy siguen legibles.
- [ ] `public-media` creado y limitado.
- [ ] Buckets privados creados.
- [ ] `file_assets` y tablas relacionadas.
- [ ] Ownership único.
- [ ] Sensibles nunca PUBLIC.
- [ ] Paths generados y verificados.
- [ ] Storage RLS deny-by-default.
- [ ] RPC con actor `auth.uid()`.
- [ ] `SECURITY DEFINER` y `search_path` fijo.
- [ ] Signed URLs temporales no persistidas.
- [ ] Retención y legal hold.
- [ ] Auditoría de accesos sensibles.
- [ ] Repositorios Supabase.
- [ ] Mocks preservados.
- [ ] DataProvider correcto.
- [ ] Compatibilidad legacy.
- [ ] Integración base M04.
- [ ] Tests verdes.
- [ ] Build y lint verdes.
- [ ] Staging declarado honestamente.
- [ ] Documentos de salida creados.
- [ ] Sin UI nueva.
- [ ] Sin Etapa 4.
- [ ] Sin M06.
- [ ] Sin merge a main.

---

## 25. Parada

No iniciar M05 Etapa 4.

No iniciar M06.

No hacer merge a `main`.

No aplicar en producción.

Detenerse cuando existan:

```text
/docs/02-arquitectura/M05-etapa-3-cierre.md
/docs/04-calidad/M05-pruebas-persistencia-storage-rls-rpc.md
```
