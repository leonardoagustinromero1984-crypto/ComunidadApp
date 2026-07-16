# LEOVER — M04 Administración, Moderación y Soporte

**Módulo:** M04 — Administración, Moderación y Soporte  
**Versión:** 1.0  
**Estado:** Autorizado únicamente para Etapa 1 — Auditoría y diseño  
**Dependencias:** M00, M01, M02 y M03 cerrados a nivel código y calidad local  
**Backend oficial:** Supabase  
**Condición de release heredada:** validación staging de migraciones 014–021 pendiente  
**Regla principal:** auditar y reutilizar los mecanismos existentes de reportes, estados, permisos e historial antes de crear nuevos casos, colas o pantallas.

---

## 1. Objetivo

M04 establecerá la base administrativa de Leover para:

- moderar contenido y perfiles;
- investigar reportes;
- aplicar medidas proporcionales;
- gestionar apelaciones;
- verificar organizaciones;
- atender solicitudes de soporte;
- mantener trazabilidad y auditoría;
- operar colas administrativas con permisos server-side.

M04 no reemplaza:

- los roles de plataforma de M02;
- los roles internos de organizaciones de M03;
- la lógica funcional de adopciones, servicios, pagos o marketplace;
- la inteligencia artificial de M26.

---

## 2. Documentos obligatorios

Leer antes de trabajar:

1. `/docs/01-producto/D01-Modulos-y-Orden.md`
2. `/docs/02-arquitectura/M00-cierre-final.md`
3. `/docs/02-arquitectura/M01-cierre-final.md`
4. `/docs/02-arquitectura/M02-cierre-final.md`
5. `/docs/02-arquitectura/M03-cierre-final.md`
6. `/docs/03-modulos/M02-Usuarios-Roles-y-Permisos.md`
7. `/docs/03-modulos/M03-Organizaciones-y-Equipos.md`
8. ADR-0001 a ADR-0005
9. Este documento.

Las decisiones vigentes de M00–M03 tienen prioridad sobre instrucciones históricas incompatibles.

---

## 3. Principios

1. La UI no es la barrera de seguridad final.
2. Toda acción administrativa se valida server-side.
3. El actor se obtiene desde `auth.uid()`.
4. Deny-by-default.
5. Moderación, soporte y verificación son dominios relacionados, pero no idénticos.
6. Toda medida debe tener motivo codificado, actor, fecha y evidencia.
7. La severidad debe ser proporcional.
8. Las sanciones reversibles deben poder revisarse.
9. Las apelaciones no pueden ser resueltas por el mismo actor cuando exista conflicto.
10. Los datos sensibles se muestran solo con permiso explícito.
11. No borrar evidencia necesaria sin política.
12. No automatizar decisiones finales con IA en M04.

---

## 4. Capacidades futuras

### 4.1 Reportes

Permitir reportar:

```text
USER_PROFILE
ORGANIZATION
POST
COMMENT
MESSAGE
PET_PROFILE
ADOPTION_LISTING
LOST_FOUND_CASE
SERVICE_PROFILE
PRODUCT
EVENT
OTHER
```

No todos estos recursos existen hoy. La auditoría debe identificar cuáles están implementados y cuáles son futuros.

Estados:

```text
OPEN
TRIAGED
IN_REVIEW
ACTION_REQUIRED
RESOLVED
DISMISSED
DUPLICATE
CLOSED
```

Prioridades:

```text
LOW
NORMAL
HIGH
URGENT
```

### 4.2 Casos de moderación

Un caso podrá agrupar:

- uno o más reportes;
- recurso objetivo;
- usuario u organización relacionada;
- evidencia;
- notas internas;
- asignado;
- prioridad;
- estado;
- medidas;
- apelaciones;
- historial.

El caso no debe exponer notas internas al reportante.

### 4.3 Medidas

Catálogo inicial:

```text
NO_ACTION
CONTENT_HIDDEN
CONTENT_REMOVED
WARNING
FEATURE_RESTRICTED
ACCOUNT_RESTRICTED
ACCOUNT_SUSPENDED
ACCOUNT_BANNED
ORGANIZATION_RESTRICTED
ORGANIZATION_SUSPENDED
VERIFICATION_REJECTED
VERIFICATION_REVOKED
```

Reglas:

- M04 utiliza los estados de cuenta M02 y estados de organización M03;
- no duplica sus columnas;
- aplicar una medida requiere permiso específico;
- las medidas temporales tienen expiración;
- toda medida genera historial;
- el último SUPERADMIN y los controles M02 siguen protegidos.

### 4.4 Apelaciones

Estados:

```text
SUBMITTED
UNDER_REVIEW
UPHELD
OVERTURNED
PARTIALLY_OVERTURNED
REJECTED
CLOSED
```

Reglas:

- plazo configurable;
- una apelación por medida o versión;
- no resolución automática;
- separación de funciones cuando sea posible;
- evidencia y decisión auditadas;
- la apelación no garantiza restauración inmediata.

### 4.5 Verificación de organizaciones

M03 dejó:

```text
NOT_REQUESTED
PENDING
VERIFIED
REJECTED
EXPIRED
```

M04 completará:

- cola de solicitudes;
- documentos y metadatos;
- revisión;
- aprobación/rechazo;
- vencimiento;
- revocación;
- historial;
- notas internas.

La organización nunca puede aprobarse a sí misma.

### 4.6 Soporte base

Tipos iniciales:

```text
ACCOUNT_ACCESS
PROFILE
ORGANIZATION
TECHNICAL
PRIVACY
SAFETY
CONTENT
OTHER
```

Estados:

```text
OPEN
IN_PROGRESS
WAITING_USER
WAITING_INTERNAL
RESOLVED
CLOSED
```

M04 implementará soporte interno básico, no un centro omnicanal completo.

### 4.7 Colas y asignación

- cola de moderación;
- cola de verificación;
- cola de soporte;
- filtros;
- prioridad;
- asignación;
- reasignación;
- vencimientos operativos;
- historial.

No implementar optimización automática o IA.

---

## 5. Permisos de plataforma

M04 reutiliza M02.

Permisos candidatos a auditar:

```text
moderation.view
moderation.manage_reports
moderation.manage_cases
moderation.apply_actions
moderation.view_sensitive
moderation.review_appeals
organizations.review_verification
organizations.revoke_verification
support.view
support.manage
support.view_sensitive
audit.view
```

No crear roles nuevos sin demostrar que MODERATOR, ADMIN y SUPERADMIN no alcanzan.

Los roles internos M03 no conceden permisos de M04.

---

## 6. Modelo técnico propuesto para revisar

No implementar antes de la auditoría.

Tablas candidatas:

```text
moderation_reports
moderation_cases
moderation_case_reports
moderation_evidence
moderation_case_notes
moderation_actions
moderation_appeals
support_tickets
support_ticket_messages
organization_verification_documents
organization_verification_reviews
administrative_assignments
administrative_audit_log
```

La auditoría debe determinar:

- qué tablas ya existen;
- qué puede extenderse;
- qué debe mantenerse separado;
- qué datos deben retenerse;
- qué campos requieren cifrado o acceso restringido.

---

## 7. Privacidad y evidencia

La evidencia puede contener:

- texto;
- referencias a recursos;
- capturas o archivos;
- metadata técnica mínima;
- timestamps;
- actor.

Reglas:

- no copiar contraseñas, tokens o secretos;
- no almacenar más PII de la necesaria;
- separar datos públicos e internos;
- controlar descargas;
- URLs firmadas temporales;
- retención documentada;
- acceso auditado;
- borrado o anonimización cuando corresponda;
- no registrar conversaciones privadas completas sin política y necesidad.

---

## 8. Relación con módulos previos

| Concepto | Propietario |
|---|---|
| Sesión, identidad y recuperación | M01 |
| Roles plataforma y estados de cuenta | M02 |
| Organización, membresía y verificación solicitada | M03 |
| Moderación, verificación revisada y soporte | M04 |
| Archivos/media base | M05 |
| Notificaciones | M06 |
| Auditoría/observabilidad transversal | M07 |
| IA de clasificación o asistencia | M26 |

M04 puede definir contratos de evidencia, pero la infraestructura general de archivos deberá alinearse con M05.

---

## 9. Seguridad

- actor desde `auth.uid()`;
- permisos desde M02;
- roles internos M03 sin autoridad administrativa global;
- `SECURITY DEFINER` con `search_path` fijo;
- sin escritura sensible directa desde Android;
- acceso a evidencia mediante URLs firmadas;
- notas internas nunca públicas;
- decisiones y cambios de estado auditados;
- asignación y reasignación registradas;
- acciones irreversibles con confirmación;
- deny ante error;
- no incluir service role en Android;
- no usar AccountType o active_modules como autoridad;
- no permitir que el mismo usuario modere su propio caso o apelación cuando exista conflicto;
- protección contra abuso de reportes y spam.

---

## 10. Android y administración

Reutilizar:

- `PermissionRepository`;
- `PlatformAdministrationRepository`;
- `AuthorizationService`;
- `AppResult`;
- `AppError`;
- `AppLogger`;
- componentes UI M00;
- estados y gates M01–M03.

Posibles dominios:

```text
domain/moderation/
domain/support/
domain/verification/
```

Posibles repositorios:

```text
ModerationRepository
SupportRepository
OrganizationVerificationRepository
```

No mezclar moderación dentro de `UserRepository` u `OrganizationRepository`.

---

## 11. Fuera de alcance de M04

- IA de moderación autónoma;
- análisis biométrico;
- pagos y disputas financieras;
- reclamos de marketplace;
- historias clínicas;
- gestión completa de adopciones;
- notificaciones multicanal completas;
- chat de soporte en tiempo real;
- producción;
- nuevo backend;
- Hilt;
- Retrofit;
- renombre de paquete.

---

## 12. Etapas de M04

### Etapa 1 — Auditoría y diseño

Crear:

```text
/docs/02-arquitectura/M04-auditoria-inicial.md
```

Sin modificar funcionalidad.

### Etapa 2 — Contratos de dominio

- reportes;
- casos;
- medidas;
- apelaciones;
- soporte;
- verificación;
- validadores;
- tests.

### Etapa 3 — Persistencia, RLS y colas

- migraciones;
- RPC;
- RLS;
- asignaciones;
- perfil administrativo;
- tests.

### Etapa 4 — UI y flujos operativos

- bandejas;
- detalle;
- acciones;
- apelaciones;
- verificación;
- soporte;
- tests.

### Etapa 5 — Calidad y cierre

- seguridad;
- staging;
- documentación;
- cierre final.

---

## 13. Instrucción vigente: solo Etapa 1

Trabajar en:

```text
m04/administracion-moderacion-soporte-auditoria
```

Auditar:

- `AdminModerationScreen` y ViewModels;
- `PlatformRepository`;
- tablas de reports, blocks, content flags y audit;
- permisos M02 de moderación/administración;
- account status e historiales;
- solicitudes de verificación M03;
- estados de organización;
- soporte o tickets existentes;
- archivos/evidencia;
- Storage y políticas;
- rutas administrativas;
- PII visible;
- notas internas;
- asignación de casos;
- apelaciones;
- rate limits/abuso;
- migraciones 001–021;
- tests;
- documentación legacy.

No hacer:

- migraciones;
- RLS;
- tablas nuevas;
- repositorios nuevos;
- pantallas nuevas;
- acciones reales;
- M05;
- merge a main;
- staging;
- producción.

Verificar:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Detenerse cuando exista:

```text
/docs/02-arquitectura/M04-auditoria-inicial.md
```

La auditoría debe incluir:

- estado Git;
- inventario SQL/Android/UI;
- reutilización;
- duplicaciones;
- permisos existentes;
- riesgos de seguridad y privacidad;
- gaps contra M04;
- propuesta de modelo;
- tablas candidatas;
- archivos a crear/modificar;
- plan por etapas;
- decisiones que requieren aprobación;
- calidad local;
- estado remoto declarado honestamente.
