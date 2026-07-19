# LEOVER — M05 Archivos, Media y Documentos

**Módulo:** M05 — Archivos, Media y Documentos  
**Versión:** 1.0  
**Estado:** autorizado únicamente para Etapa 1 — Auditoría y diseño  
**Dependencias:** M00, M01, M02, M03 y M04 cerrados a nivel código y calidad local  
**Backend oficial:** Supabase  
**Condición de release heredada:** migraciones `014`–`023` pendientes de validación remota  
**Regla principal:** inventariar y reutilizar Storage, servicios, contratos y rutas ya existentes antes de crear buckets, tablas o flujos nuevos.

---

## 1. Objetivo

M05 será la capa transversal de archivos de LeoVer.

Debe proporcionar contratos y mecanismos seguros para:

- avatares;
- logos y portadas;
- imágenes de mascotas;
- imágenes de publicaciones;
- documentos de organizaciones;
- evidencia administrativa;
- archivos de adopciones;
- archivos de casos de mascotas perdidas;
- documentos de proveedores;
- adjuntos futuros de soporte y mensajería;
- metadatos, versiones y retención;
- acceso público, privado o restringido;
- subida, reemplazo, descarga y eliminación;
- limpieza de archivos huérfanos;
- auditoría de operaciones sensibles.

M05 no debe asumir que todos los archivos son públicos.

---

## 2. Dependencias y autoridad

| Área | Módulo propietario |
|---|---|
| Identidad y sesión | M01 |
| Usuarios, roles y estados | M02 |
| Organizaciones y permisos internos | M03 |
| Moderación, soporte y evidencia administrativa | M04 |
| Archivos, media y documentos | M05 |
| Notificaciones | M06 |
| Auditoría y observabilidad transversal | M07 |

Reglas:

- M05 no crea un segundo sistema de autenticación.
- El actor se deriva de `auth.uid()`.
- Los permisos globales provienen de M02.
- Los permisos organizacionales provienen de M03.
- M04 consume referencias de evidencia definidas por M05.
- La UI nunca sustituye RLS, policies o RPC.
- `AccountType` y `active_modules` no conceden acceso a archivos.

---

## 3. Principios

1. Privado por defecto.
2. Público únicamente por decisión explícita.
3. Separar archivo físico, metadatos y relación con el recurso.
4. No almacenar base64 en tablas.
5. No guardar URLs firmadas como permanentes.
6. No confiar en nombre de archivo o MIME enviado por cliente.
7. Limitar tamaño, cantidad y tipos.
8. Evitar sobrescrituras accidentales.
9. Usar rutas determinísticas y ownership verificable.
10. Mantener trazabilidad de upload, replace y delete.
11. No exponer documentos internos mediante buckets públicos.
12. No borrar evidencia o documentos sujetos a retención sin política.
13. Evitar archivos huérfanos.
14. Deny-by-default ante error.
15. No usar service role en Android.

---

## 4. Clasificación de archivos

### 4.1 Públicos

Ejemplos:

- avatar público;
- logo de organización;
- portada pública;
- imagen pública de mascota;
- imagen pública de una publicación.

Incluso cuando son públicos:

- la escritura sigue siendo privada;
- la eliminación requiere ownership o permiso;
- la ruta no debe permitir sobrescribir archivos ajenos;
- la visibilidad debe ser explícita;
- los metadatos internos no son públicos.

### 4.2 Privados del usuario

Ejemplos:

- documentos personales;
- borradores;
- adjuntos privados;
- archivos no publicados;
- material de soporte del solicitante.

Acceso:

- propietario;
- staff autorizado cuando corresponda;
- URL firmada temporal;
- sin listado global.

### 4.3 Privados de organización

Ejemplos:

- documentos de verificación;
- documentación institucional;
- archivos internos;
- documentos de miembros;
- material no público de sucursales.

Acceso:

- miembros autorizados según permisos M03;
- revisores autorizados M04;
- sin exposición por perfil público.

### 4.4 Sensibles o administrativos

Ejemplos:

- evidencia de moderación;
- documentos de verificación;
- archivos de soporte PRIVACY/SAFETY;
- documentos legales;
- material sujeto a retención.

Requisitos:

- bucket privado;
- acceso explícito;
- URL firmada corta;
- acceso auditado;
- retención definida;
- sin logs de contenido;
- sin thumbnails públicos;
- sin descarga por rutas predecibles.

---

## 5. Tipos funcionales iniciales

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

No todos los módulos existen todavía.  
La auditoría debe diferenciar:

- implementado;
- parcial;
- futuro;
- fuera de alcance actual.

---

## 6. Modelo conceptual

No implementar antes de la auditoría.

Entidades candidatas:

```text
file_assets
file_asset_versions
file_asset_links
file_upload_sessions
file_access_audit
file_retention_policies
file_processing_jobs
```

### `file_assets`

Campos conceptuales:

```text
id
owner_user_id?
owner_organization_id?
purpose
visibility
storage_bucket
storage_path
original_filename
safe_filename
declared_mime_type
detected_mime_type
size_bytes
checksum
status
created_by
created_at
updated_at
deleted_at?
retention_until?
```

### `file_asset_links`

Relaciona un archivo con un recurso:

```text
file_asset_id
resource_type
resource_id
relation_type
sort_order?
is_primary
created_at
```

Reglas:

- ownership único y claro;
- un archivo puede vincularse a un recurso;
- reutilización múltiple solo si se aprueba explícitamente;
- eliminar relación no siempre elimina archivo;
- eliminación física depende de referencias y retención.

### Estados candidatos

```text
PENDING_UPLOAD
UPLOADED
PROCESSING
READY
REJECTED
QUARANTINED
DELETED
FAILED
```

### Visibilidad

```text
PUBLIC
OWNER_ONLY
ORGANIZATION_PRIVATE
AUTHORIZED_STAFF
RESOURCE_PARTICIPANTS
SIGNED_LINK_ONLY
```

---

## 7. Convención de rutas

Las rutas deben impedir colisiones y acceso cruzado.

Ejemplos conceptuales:

```text
users/{userId}/avatars/{assetId}/{filename}
users/{userId}/private/{assetId}/{filename}

pets/{petId}/gallery/{assetId}/{filename}

organizations/{organizationId}/logo/{assetId}/{filename}
organizations/{organizationId}/cover/{assetId}/{filename}
organizations/{organizationId}/documents/{assetId}/{filename}
organizations/{organizationId}/verification/{assetId}/{filename}

moderation/cases/{caseId}/evidence/{assetId}/{filename}
support/tickets/{ticketId}/attachments/{assetId}/{filename}
```

Reglas:

- no confiar solo en el path para autorización;
- `userId` u `organizationId` del path deben validarse server-side;
- usar identificadores no predecibles para assets;
- sanitizar filename;
- evitar traversal;
- no aceptar path arbitrario desde Android;
- no permitir que el cliente elija bucket sensible.

---

## 8. Buckets actuales a auditar

La auditoría debe confirmar en el repositorio y migraciones:

### `leover`

- estado público/privado;
- usos actuales;
- rutas;
- policies;
- riesgo de exposición;
- si debe deprecarse o limitarse;
- no usar para documentos sensibles.

### `profile-avatars`

- ownership;
- visibilidad;
- reemplazo;
- eliminación;
- límites;
- signed/public URL;
- orphan cleanup.

### `organization-media`

- condición privada;
- rutas `logo|cover`;
- permisos `organization.update`;
- URLs firmadas;
- ownership;
- archivos huérfanos;
- relación con perfil público.

También auditar cualquier bucket adicional encontrado.

No crear bucket nuevo en Etapa 1.

---

## 9. Seguridad de upload

Validaciones mínimas futuras:

- tamaño máximo por propósito;
- cantidad máxima;
- extensión permitida;
- MIME declarado;
- MIME detectado;
- magic bytes;
- checksum;
- nombre sanitizado;
- dimensiones para imágenes;
- orientación EXIF;
- eliminación de metadata sensible cuando corresponda;
- rechazo de ejecutables;
- rechazo de SVG no seguro cuando corresponda;
- rechazo de HTML;
- bloqueo de doble extensión;
- protección de zip bombs;
- antivirus o scanning futuro;
- rate limit;
- cuota por usuario u organización.

La Etapa 1 debe identificar qué validaciones ya existen realmente.

No afirmar antivirus si no existe.

---

## 10. Imágenes y procesamiento

Capacidades futuras:

- compresión;
- resize;
- thumbnails;
- conversión a formatos seguros;
- corrección de orientación;
- eliminación de EXIF;
- variantes;
- placeholder;
- reintento de procesamiento;
- estado `PROCESSING`;
- fallback al original cuando sea seguro.

La auditoría debe determinar:

- si Android comprime localmente;
- si Supabase transforma;
- si se guarda original;
- si existen thumbnails;
- si hay URLs públicas persistentes;
- si se controla dimensión y peso.

No implementar pipelines de procesamiento en Etapa 1.

---

## 11. Documentos

Tipos esperados:

```text
PDF
JPG
JPEG
PNG
WEBP
HEIC
DOCX
TXT
```

La lista final depende del propósito.

Reglas:

- documentos de verificación y evidencia no son públicos;
- previsualización solo si es segura;
- evitar ejecutar macros;
- DOCX se descarga, no se interpreta en Android;
- no aceptar archivos ejecutables;
- no usar nombre original como path;
- registrar tamaño y checksum;
- URL firmada temporal;
- descarga controlada;
- revocación de acceso al cambiar permisos.

---

## 12. Eliminación y retención

Diferenciar:

```text
UNLINK
SOFT_DELETE
PHYSICAL_DELETE
QUARANTINE
LEGAL_HOLD
RETENTION_EXPIRED
```

Reglas:

- una relación eliminada no implica borrar el archivo físico;
- un asset compartido no se borra mientras tenga referencias;
- evidencia M04 puede tener retención;
- documentos de verificación pueden conservarse por política;
- cuenta eliminada no debe borrar automáticamente evidencia retenida;
- toda eliminación sensible debe auditarse;
- jobs de limpieza deben ser idempotentes;
- no hacer borrado masivo desde Android.

---

## 13. Descarga y acceso

### Público

- URL pública solo para assets aprobados como `PUBLIC`;
- sin parámetros sensibles;
- caché compatible con reemplazo/versionado.

### Privado

- URL firmada temporal;
- duración acotada;
- generada después de autorización;
- no persistir la URL;
- refrescar cuando expire;
- no registrar query tokens.

### Revocación

- un cambio de permisos debe impedir nuevas URLs;
- una URL ya emitida vive hasta su expiración;
- para alta sensibilidad usar expiración corta;
- borrar o mover el archivo cuando corresponda.

---

## 14. Android

Reutilizar y auditar:

- servicios de Storage existentes;
- repositorios M01/M02/M03/M04;
- `AppResult`;
- `AppError`;
- `AppLogger`;
- providers;
- estado de sesión;
- selección de imágenes/documentos;
- permisos del dispositivo;
- Activity Result APIs;
- persistencia temporal;
- progreso;
- cancelación;
- reintento;
- limpieza de URI temporales.

Contratos futuros candidatos:

```text
FileAssetRepository
FileUploadRepository
FileDownloadRepository
FileAccessRepository
FileProcessingRepository
FileRetentionRepository
```

Servicios candidatos:

```text
FileValidator
FileNameSanitizer
MimeDetector
ImageProcessor
UploadCoordinator
SignedUrlProvider
FileCleanupCoordinator
```

No crear un repositorio genérico que ignore el propósito y la autorización.

---

## 15. UI futura

Flujos candidatos:

- seleccionar archivo;
- validar;
- previsualizar;
- confirmar;
- mostrar progreso;
- cancelar;
- reintentar;
- reemplazar;
- eliminar;
- descargar;
- compartir enlace temporal;
- informar rechazo.

Estados:

```text
Idle
Selecting
Validating
Uploading
Processing
Ready
Failed
Cancelled
Deleting
```

Reglas:

- no mostrar éxito antes de confirmar persistencia;
- bloquear doble upload;
- no mantener URI sensible después de logout;
- no loguear path local completo;
- limpiar temporales;
- explicar límites;
- no pedir permisos amplios innecesarios.

---

## 16. Relación con M04

M04 ya utiliza referencias lógicas de evidencia.

M05 debe definir posteriormente:

- asset físico;
- ownership;
- upload autorizado;
- acceso staff;
- URL firmada;
- retención;
- eliminación;
- auditoría;
- vínculo con caso, verificación o ticket.

M05 no debe modificar decisiones de moderación, soporte o verificación.

---

## 17. Fuera de alcance

- IA de análisis de imágenes;
- reconocimiento facial;
- diagnóstico veterinario;
- OCR avanzado;
- antivirus propio desde cero;
- CDN propia;
- almacenamiento fuera de Supabase;
- pagos;
- GPS/mapas;
- chat en tiempo real;
- editor multimedia profesional;
- streaming de video;
- producción;
- nuevo backend;
- Hilt;
- Retrofit;
- renombre de paquete.

---

## 18. Etapas de M05

### Etapa 1 — Auditoría y diseño

Inventario completo sin cambios funcionales.

### Etapa 2 — Contratos y validadores

- modelos;
- propósitos;
- visibilidad;
- ownership;
- validadores;
- rutas;
- repositorios;
- mocks;
- pruebas.

### Etapa 3 — Persistencia, Storage y RLS

- migraciones;
- buckets;
- policies;
- RPC;
- metadatos;
- signed URLs;
- integración Supabase;
- pruebas SQL.

### Etapa 4 — UI y flujos

- upload;
- progreso;
- reemplazo;
- eliminación;
- descarga;
- integración en perfiles y M04;
- pruebas.

### Etapa 5 — Calidad y cierre

- seguridad;
- staging;
- retención;
- limpieza;
- regresión;
- cierre final.

---

## 19. Ejecución autorizada: solo Etapa 1

Trabajar en:

```text
m05/archivos-media-documentos-auditoria
```

Crear únicamente:

```text
/docs/02-arquitectura/M05-auditoria-inicial.md
```

### Auditar

#### SQL y Storage

- migraciones `001`–`023`;
- `storage.buckets`;
- `storage.objects`;
- policies;
- grants;
- buckets públicos/privados;
- rutas;
- ownership;
- signed URLs;
- delete;
- replace;
- upload;
- límites;
- MIME;
- tamaño;
- retención;
- auditoría;
- huérfanos.

#### Android

- servicios Storage;
- selección de archivos;
- upload/download/delete;
- mappers;
- repositorios;
- ViewModels;
- pantallas;
- compresión;
- nombres;
- MIME;
- progreso;
- reintento;
- logout;
- logs;
- mocks.

#### Usos funcionales

- avatar usuario;
- media de organización;
- mascotas;
- publicaciones;
- adopciones;
- perdidos/encontrados;
- servicios;
- moderación;
- verificación;
- soporte;
- mensajes;
- eventos;
- marketplace.

Clasificar cada uso:

```text
IMPLEMENTADO
PARCIAL
MOCK
FUTURO
AUSENTE
RIESGO
```

### No hacer

- no crear migraciones;
- no crear buckets;
- no modificar policies;
- no crear tablas;
- no crear repositorios;
- no cambiar pantallas;
- no tocar M06;
- no aplicar staging;
- no usar producción;
- no hacer merge a `main`;
- no implementar Storage administrativo;
- no mover archivos existentes.

### Calidad

Ejecutar:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

### Contenido obligatorio de la auditoría

- estado Git;
- inventario de buckets;
- inventario SQL/policies;
- inventario Android;
- mapa de usos;
- clasificación público/privado/sensible;
- riesgos de seguridad y privacidad;
- duplicaciones;
- reutilización;
- gaps contra M05;
- propuesta de modelo;
- propuesta de buckets y rutas;
- propuesta de permisos;
- límites y validaciones;
- retención y eliminación;
- archivos a crear/modificar;
- plan por etapas;
- decisiones que requieren aprobación;
- calidad local;
- estado remoto honesto;
- parada.

---

## 20. Criterios de aceptación de Etapa 1

- [ ] Commit base confirmado.
- [ ] Working tree limpio.
- [ ] Rama correcta.
- [ ] Sin cambios funcionales.
- [ ] Sin migraciones ni buckets nuevos.
- [ ] Buckets existentes inventariados.
- [ ] Policies y ownership inventariados.
- [ ] Servicios Android inventariados.
- [ ] Usos funcionales clasificados.
- [ ] Archivos públicos, privados y sensibles diferenciados.
- [ ] Riesgos documentados.
- [ ] No se afirma antivirus o procesamiento inexistente.
- [ ] Propuesta de modelo y rutas.
- [ ] Retención y huérfanos analizados.
- [ ] Build aprobado.
- [ ] Tests aprobados.
- [ ] Lint aprobado.
- [ ] Staging declarado honestamente.
- [ ] Auditoría creada.
- [ ] Sin M06.
- [ ] Sin merge a main.

---

## 21. Parada

No iniciar Etapa 2.

No iniciar M06.

No hacer merge a `main`.

Detenerse al crear:

```text
/docs/02-arquitectura/M05-auditoria-inicial.md
```
