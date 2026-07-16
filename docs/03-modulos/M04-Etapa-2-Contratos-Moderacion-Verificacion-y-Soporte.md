# LEOVER — M04 Etapa 2: Contratos de Moderación, Verificación y Soporte

**Módulo:** M04 — Administración, Moderación y Soporte  
**Etapa:** 2 — Contratos de dominio, autorización y repositorios  
**Estado de entrada:** Etapa 1 aprobada y consolidada  
**Commit base:** `8985c075959f2c01500d33c9345e081117f6a5f2`  
**Rama base:** `m04/administracion-moderacion-soporte-auditoria`  
**Backend oficial:** Supabase  
**Alcance:** Kotlin puro, interfaces, validadores, reglas de autorización y pruebas unitarias.  
**Prohibido en esta etapa:** SQL, RLS, migraciones, RPC, pantallas, navegación, Storage, staging y producción.

---

## 1. Objetivo

Definir contratos estables para M04 antes de persistir datos o ampliar la UI.

La Etapa 2 debe entregar:

- reportes de moderación compatibles con `content_reports`;
- casos que agrupen reportes;
- medidas administrativas tipadas;
- apelaciones;
- revisión de verificación de organizaciones;
- tickets de soporte;
- asignación administrativa;
- reglas de autorización y conflicto de interés;
- repositorios abstractos;
- mocks o fakes deterministas;
- pruebas unitarias completas.

No debe corregir todavía la RLS crítica de `content_reports`. Esa corrección será el primer bloque obligatorio de la Etapa 3 mediante una migración nueva posterior a `021`.

---

## 2. Documentos obligatorios

Leer en este orden:

1. `/docs/01-producto/D01-Modulos-y-Orden.md`
2. `/docs/02-arquitectura/M00-cierre-final.md`
3. `/docs/02-arquitectura/M01-cierre-final.md`
4. `/docs/02-arquitectura/M02-cierre-final.md`
5. `/docs/02-arquitectura/M03-cierre-final.md`
6. `/docs/02-arquitectura/M04-auditoria-inicial.md`
7. `/docs/03-modulos/M02-Usuarios-Roles-y-Permisos.md`
8. `/docs/03-modulos/M03-Organizaciones-y-Equipos.md`
9. `/docs/03-modulos/M04-Administracion-Moderacion-y-Soporte.md`
10. ADR-0001 a ADR-0005
11. Este documento.

---

## 3. Protección Git

Antes de trabajar:

1. Confirmar commit base:

```text
8985c075959f2c01500d33c9345e081117f6a5f2
```

2. Confirmar working tree limpio.
3. Crear rama:

```text
m04/etapa-2-contratos-moderacion-verificacion-soporte
```

4. No incorporar `wip/gps-mapas-pagos`.
5. No hacer merge a `main`.
6. No iniciar M05.

---

## 4. Decisiones aprobadas

### 4.1 Evolución de reportes

Se mantiene `content_reports` como origen legacy y entrada compatible.

No crear una segunda tabla duplicada en Etapa 2.

Los contratos nuevos deben:

- aceptar los targets legacy `POST`, `USER` y `COMMENT`;
- permitir targets futuros sin acoplarse a tablas aún inexistentes;
- representar un target mediante tipo + identificador;
- soportar migración posterior hacia casos de moderación;
- no asumir que `ACTIONED` significa una medida real.

### 4.2 Permisos

M04 reutiliza el sistema de permisos M02.

Contratos candidatos:

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

En Etapa 2 solo se definen códigos y reglas de autorización.  
Los seeds se evaluarán e implementarán en Etapa 3.

### 4.3 Evidencia

M04 define el contrato lógico de evidencia, pero no crea bucket ni políticas.

La infraestructura física debe alinearse con M05.

### 4.4 Soporte

El soporte inicial será:

- tickets internos;
- mensajes asincrónicos;
- asignación;
- estados;
- prioridad;
- historial.

No habrá chat en tiempo real.

### 4.5 Verificación

M03 solicita verificación.  
M04 revisa, aprueba, rechaza, revoca o marca vencida.

La organización no puede revisar su propia solicitud.

### 4.6 Privacidad

`reporterId`, evidencia, notas internas y datos de contacto sensibles requieren permiso explícito.

Los modelos públicos no deben incluirlos por defecto.

---

## 5. Paquetes y archivos esperados

Adaptar nombres a la convención real del proyecto, sin renombrar paquetes existentes.

### 5.1 Dominio de moderación

Crear bajo una ubicación equivalente a:

```text
app/src/main/java/.../domain/moderation/
```

Archivos candidatos:

```text
ModerationReport.kt
ModerationTarget.kt
ModerationCase.kt
ModerationAction.kt
ModerationAppeal.kt
ModerationEvidence.kt
ModerationAssignment.kt
ModerationReason.kt
ModerationValidators.kt
```

### 5.2 Verificación

```text
app/src/main/java/.../domain/verification/
```

Archivos candidatos:

```text
OrganizationVerificationReview.kt
OrganizationVerificationDecision.kt
OrganizationVerificationDocumentRef.kt
VerificationValidators.kt
```

### 5.3 Soporte

```text
app/src/main/java/.../domain/support/
```

Archivos candidatos:

```text
SupportTicket.kt
SupportMessage.kt
SupportCategory.kt
SupportPriority.kt
SupportValidators.kt
```

### 5.4 Autorización

Crear o extender una ubicación equivalente a:

```text
domain/moderation/authorization/
```

Archivos candidatos:

```text
AdministrativePermissionCode.kt
ModerationAuthorization.kt
AdministrativeConflictRules.kt
```

### 5.5 Repositorios

Crear interfaces dedicadas, sin mezclarlas en `UserRepository`, `OrganizationRepository` ni `PlatformAdministrationRepository`.

```text
ModerationRepository
OrganizationVerificationRepository
SupportRepository
AdministrativeAuditRepository
```

Mocks/fakes:

```text
MockModerationRepository
MockOrganizationVerificationRepository
MockSupportRepository
MockAdministrativeAuditRepository
```

No crear implementaciones Supabase en esta etapa.

---

## 6. Contratos de moderación

### 6.1 Target

Debe permitir referencias genéricas y seguras:

```kotlin
data class ModerationTargetRef(
    val type: ModerationTargetType,
    val targetId: String
)
```

Tipos iniciales:

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

Compatibilidad legacy:

```text
USER → USER_PROFILE
POST → POST
COMMENT → COMMENT
```

Validaciones:

- `targetId` no vacío;
- longitud razonable;
- `OTHER` requiere descripción controlada;
- no resolver ni consultar el recurso desde el modelo;
- no incluir contenido sensible embebido.

### 6.2 Reporte

Campos mínimos:

```text
id
reporterId
target
reasonCode
description
priority
status
createdAt
updatedAt
caseId?
duplicateOfReportId?
```

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

Reglas:

- un reporte nuevo empieza en `OPEN`;
- solo staff autorizado cambia prioridad o estado;
- `DUPLICATE` requiere `duplicateOfReportId`;
- `RESOLVED` no implica necesariamente sanción;
- `reporterId` es sensible;
- description debe tener límite y sanitización;
- no permitir secretos, tokens o contraseñas en mensajes de validación/logs.

### 6.3 Caso

Campos mínimos:

```text
id
title
status
priority
assignedToUserId?
createdByUserId
createdAt
updatedAt
closedAt?
```

Estados:

```text
OPEN
TRIAGED
IN_REVIEW
ACTION_REQUIRED
RESOLVED
DISMISSED
CLOSED
```

Reglas:

- un caso agrupa uno o más reportes;
- un reporte no puede estar activo en dos casos distintos;
- cerrar requiere resolución o motivo;
- asignación y reasignación deben registrarse;
- notas internas no forman parte del modelo público;
- un actor no debe asignarse o resolver un caso con conflicto de interés.

### 6.4 Medidas

Tipos:

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

Campos:

```text
id
caseId
target
actionType
reasonCode
reasonDetail?
appliedByUserId
appliedAt
expiresAt?
reversedAt?
reversedByUserId?
```

Reglas:

- `NO_ACTION` no modifica recursos;
- medidas temporales requieren `expiresAt`;
- medidas permanentes no usan expiración;
- medidas de cuenta reutilizan estados M02;
- medidas de organización reutilizan estados M03;
- M04 no duplica columnas de estado;
- aplicar medidas requiere `moderation.apply_actions`;
- revocar verificación requiere permiso específico;
- toda medida debe producir historial futuro.

### 6.5 Apelaciones

Campos:

```text
id
actionId
submittedByUserId
statement
status
reviewedByUserId?
decisionReason?
createdAt
reviewedAt?
```

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

- solo el afectado o representante permitido puede apelar;
- una apelación activa por medida;
- no puede revisarla quien aplicó la medida;
- no se puede resolver sin `decisionReason`;
- `OVERTURNED` y `PARTIALLY_OVERTURNED` requieren acción correctiva posterior;
- no restaurar automáticamente desde el modelo de dominio;
- plazo configurable mediante contrato, no hardcode disperso.

---

## 7. Verificación de organizaciones

Modelo de revisión:

```text
id
organizationId
requestedByUserId
assignedToUserId?
status
decision
reviewNote?
createdAt
updatedAt
decidedAt?
decidedByUserId?
```

Decisiones:

```text
APPROVE
REJECT
REQUEST_MORE_INFORMATION
REVOKE
MARK_EXPIRED
```

Reglas:

- solo organizaciones en `PENDING` pueden aprobarse/rechazarse;
- `REQUEST_MORE_INFORMATION` no marca VERIFIED;
- aprobación produce `VERIFIED`;
- rechazo produce `REJECTED`;
- revocación aplica solo a VERIFIED;
- vencimiento aplica según política;
- miembro de la organización no puede revisar su propia organización;
- `reviewNote` es interna;
- documentos se referencian mediante IDs lógicos, no URLs permanentes;
- no incluir binarios o base64 en modelos.

---

## 8. Soporte

### 8.1 Ticket

Campos:

```text
id
requesterUserId
category
subject
description
priority
status
assignedToUserId?
createdAt
updatedAt
resolvedAt?
closedAt?
```

Categorías:

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

Reglas:

- requester crea en `OPEN`;
- solo staff asigna prioridad operativa;
- `RESOLVED` puede reabrirse según política;
- `CLOSED` requiere resolución previa o motivo;
- tickets de privacidad y seguridad requieren tratamiento sensible;
- no mezclar soporte con moderación salvo vínculo explícito;
- no usar chats existentes como soporte.

### 8.2 Mensajes

Campos:

```text
id
ticketId
authorUserId
visibility
body
createdAt
```

Visibilidad:

```text
REQUESTER_VISIBLE
INTERNAL
```

Reglas:

- mensaje interno nunca se expone al solicitante;
- cuerpo limitado y sanitizado;
- sin adjuntos físicos en Etapa 2;
- referencias de evidencia opcionales mediante contrato lógico.

---

## 9. Asignación y conflicto de interés

Definir reglas puras para:

- asignar caso/ticket/revisión;
- reasignar;
- autoasignación permitida solo con permiso;
- impedir asignación a usuario sin permiso;
- impedir revisión propia;
- impedir apelación resuelta por quien aplicó la medida;
- impedir verificación de organización propia;
- deny ante datos faltantes o error.

El resultado debe ser explícito:

```text
ALLOWED
DENIED_MISSING_PERMISSION
DENIED_CONFLICT_OF_INTEREST
DENIED_INVALID_STATE
DENIED_SENSITIVE_ACCESS
DENIED_UNKNOWN
```

No usar simples booleanos cuando se necesite explicar el rechazo.

---

## 10. Repositorios

### ModerationRepository

Contratos candidatos:

```text
createReport
getMyReports
getReportForStaff
listModerationQueue
createCase
attachReportToCase
assignCase
changeCaseStatus
recordAction
submitAppeal
listAppeals
reviewAppeal
```

### OrganizationVerificationRepository

```text
listPendingVerificationRequests
getVerificationReview
assignVerificationReview
recordVerificationDecision
```

### SupportRepository

```text
createTicket
getMyTickets
getTicket
listSupportQueue
assignTicket
changeTicketStatus
addRequesterMessage
addInternalMessage
```

### AdministrativeAuditRepository

```text
recordAdministrativeEvent
listAdministrativeEvents
```

En Etapa 2:

- interfaces;
- mocks/fakes;
- errores tipados;
- resultados `AppResult`;
- sin Supabase.

---

## 11. Autorización

Definir reglas puras que reciban permisos efectivos de M02.

### Matriz mínima

| Acción | Permiso |
|---|---|
| Ver cola básica | `moderation.view` |
| Gestionar reportes | `moderation.manage_reports` |
| Gestionar casos | `moderation.manage_cases` |
| Aplicar medidas | `moderation.apply_actions` |
| Ver reporter/evidencia | `moderation.view_sensitive` |
| Revisar apelaciones | `moderation.review_appeals` |
| Revisar verificación | `organizations.review_verification` |
| Revocar verificación | `organizations.revoke_verification` |
| Ver soporte | `support.view` |
| Gestionar soporte | `support.manage` |
| Ver soporte sensible | `support.view_sensitive` |
| Ver auditoría | `audit.view` |

Reglas:

- roles M03 no conceden ninguno;
- AccountType/active_modules no conceden ninguno;
- plataforma SUPERADMIN no debe asumirse en Android sin permisos efectivos;
- error de permisos → deny;
- datos sensibles requieren permiso adicional;
- permiso de lectura no implica mutación;
- permiso de moderación no implica soporte;
- permiso de soporte no implica verificación.

---

## 12. Validadores

Probar como mínimo:

- IDs y texto vacío;
- límites de longitud;
- transición de estados;
- expiración;
- duplicados;
- medida temporal sin vencimiento;
- medida permanente con vencimiento inválido;
- apelación duplicada;
- conflicto de interés;
- decisión sin motivo;
- ticket cerrado sin resolución;
- mensaje interno;
- acceso sensible;
- compatibilidad de targets legacy;
- unknown/error → deny.

No introducir dependencias Android en validadores puros.

---

## 13. Pruebas obligatorias

Crear pruebas equivalentes a:

```text
ModerationReportRulesTest
ModerationCaseRulesTest
ModerationActionRulesTest
ModerationAppealRulesTest
OrganizationVerificationRulesTest
SupportTicketRulesTest
AdministrativeAuthorizationTest
AdministrativeConflictRulesTest
ModerationLegacyCompatibilityTest
```

Requisitos:

- conservar las 172 pruebas existentes;
- agregar cobertura de permitidos y denegados;
- no depender de red, reloj real o Supabase;
- usar reloj inyectable cuando haya vencimientos;
- mocks deterministas;
- 0 fallos y 0 errores.

---

## 14. Calidad

Ejecutar:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Documentar:

- cantidad total de pruebas;
- build;
- lint;
- archivos creados/modificados;
- deuda;
- estado remoto.

---

## 15. Documento de cierre

Crear exactamente:

```text
/docs/02-arquitectura/M04-etapa-2-cierre.md
```

Debe incluir:

- rama;
- commit base;
- archivos;
- contratos creados;
- decisiones;
- compatibilidad legacy;
- autorización;
- conflictos;
- pruebas;
- build/lint;
- riesgos;
- staging 014–021 pendiente;
- checklist;
- parada.

---

## 16. Criterios de aceptación

- [ ] Working tree inicial limpio.
- [ ] Rama correcta.
- [ ] Sin SQL, RLS o migraciones.
- [ ] Sin pantallas o navegación.
- [ ] `content_reports` mantenido como entrada compatible.
- [ ] Targets legacy mapeados.
- [ ] Contratos de reportes y casos.
- [ ] Contratos de medidas y apelaciones.
- [ ] Contratos de verificación.
- [ ] Contratos de soporte.
- [ ] Repositorios dedicados.
- [ ] Mocks/fakes deterministas.
- [ ] Permisos M02 reutilizados.
- [ ] Roles M03 sin autoridad global.
- [ ] AccountType/active_modules sin autoridad.
- [ ] Datos sensibles separados.
- [ ] Conflictos de interés denegados.
- [ ] Deny-by-default.
- [ ] Pruebas verdes.
- [ ] Build y lint verdes.
- [ ] Cierre creado.
- [ ] Sin M05.
- [ ] Sin merge a main.

---

## 17. Parada

No iniciar Etapa 3.

No corregir todavía la RLS mediante SQL.

No hacer merge a `main`.

Detenerse al crear:

```text
/docs/02-arquitectura/M04-etapa-2-cierre.md
```
