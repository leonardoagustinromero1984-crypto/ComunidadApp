# LEOVER — M06 Etapa 2: Contratos, Categorías, Preferencias, Deep Links e Instalaciones

**Módulo:** M06 — Notificaciones  
**Etapa:** 2 — Contratos de dominio, preferencias, deep links, instalaciones y mocks  
**Estado de entrada:** Etapa 1 aprobada y consolidada  
**Commit base:** `ee677373beec7311b029c4c7b90f4a35d7a8785a`  
**Rama base:** `m06/notificaciones-auditoria`  
**Calidad de entrada:** 358 tests, 0 failures, 0 errors; build y lint aprobados  
**Staging heredado:** migraciones `014`–`025` pendientes de validación remota  
**Alcance:** Kotlin puro, contratos, reglas de dominio, interfaces, mocks y pruebas.  
**Prohibido:** SQL, migraciones, Firebase nuevo, Edge Functions, jobs, cron, WorkManager, pantallas nuevas, producción, M07 y cambios de username/auth.

---

## 1. Objetivo

Definir la capa de dominio de M06 antes de endurecer persistencia y entrega.

La Etapa 2 debe entregar:

- categorías;
- prioridad;
- sensibilidad;
- canales;
- estados;
- eventos tipados;
- destinatarios;
- preferencias;
- quiet hours;
- instalaciones y tokens;
- deep links tipados;
- deduplicación;
- idempotencia;
- retry y dead-letter;
- contratos de bandeja in-app;
- contratos de outbox;
- repositorios;
- mocks deterministas;
- pruebas unitarias.

No debe corregir todavía `create_notification`, la policy INSERT ni `NotificationDispatcher`. Eso corresponde a la Etapa 3.

---

## 2. Documentos obligatorios

Leer:

1. `/docs/02-arquitectura/M06-auditoria-inicial.md`
2. `/docs/03-modulos/M06-Notificaciones.md`
3. `/docs/02-arquitectura/M05-cierre-final.md`
4. `/docs/04-calidad/M05-reporte-validacion-staging.md`
5. ADR-0001 a ADR-0005
6. Este documento.

---

## 3. Protección Git

1. Confirmar commit base:

```text
ee677373beec7311b029c4c7b90f4a35d7a8785a
```

2. Confirmar working tree limpio.
3. Crear rama:

```text
m06/etapa-2-contratos-preferencias-deep-links-instalaciones
```

4. No incorporar WIP GPS/mapas/pagos.
5. No hacer merge a `main`.
6. No iniciar M07.
7. No modificar username/auth.
8. No aplicar staging ni producción.

---

## 4. Decisiones obligatorias

### 4.1 Seguridad de creación

- Android no puede crear una notificación para un usuario arbitrario.
- El destinatario futuro será calculado server-side.
- Los contratos pueden representar eventos y destinatarios, pero no autorizan envío real.
- `create_notification` y la INSERT abierta quedan sin tocar en Etapa 2.
- La Etapa 3 debe revocar esa superficie insegura.

### 4.2 Fuente de verdad

- La bandeja in-app será la fuente de verdad.
- Push y correo serán deliveries.
- Un push entregado no equivale a leído.
- Múltiples dispositivos no duplican la notificación in-app.

### 4.3 Deep links

- Solo rutas tipadas y allowlisted.
- Abrir una notificación vuelve a validar sesión, recurso, permisos y organización.
- La notificación nunca concede acceso.
- Recurso inexistente o permiso denegado → fallback seguro.

### 4.4 Sensibilidad

- `SENSITIVE` y `SECURITY_CRITICAL` no exponen contenido completo en push o lock screen.
- INTERNAL nunca se dirige al solicitante.
- Signed URLs, tokens y PII completa no entran en payloads.

### 4.5 Instalaciones

- Una instalación representa un dispositivo/app installation.
- Logout revoca solo la instalación actual.
- Múltiples dispositivos son válidos.
- Cambio de usuario debe desvincular la instalación anterior.
- El token no debe aparecer en `toString`, logs o errores.

### 4.6 Preferencias

- In-app puede ser obligatoria para eventos legales, administrativos o de seguridad.
- Push/email respetan preferencias.
- Marketing OFF por defecto.
- Quiet hours usan timezone IANA.
- Seguridad crítica puede exceptuar quiet hours solo por política explícita.

---

## 5. Paquetes y archivos esperados

Crear bajo una ubicación equivalente a:

```text
app/src/main/java/.../domain/notifications/
```

Archivos candidatos:

```text
NotificationCategory.kt
NotificationPriority.kt
NotificationSensitivity.kt
NotificationChannel.kt
NotificationState.kt
NotificationDeliveryState.kt
NotificationEvent.kt
NotificationRecipient.kt
NotificationPreference.kt
NotificationQuietHours.kt
NotificationInstallation.kt
NotificationDeepLink.kt
NotificationDeduplication.kt
NotificationRetryPolicy.kt
NotificationOutbox.kt
NotificationContracts.kt
```

Autorización:

```text
domain/notifications/authorization/
NotificationAuthorization.kt
NotificationVisibilityRules.kt
NotificationDeepLinkAuthorization.kt
```

Repositorios:

```text
NotificationInboxRepository
NotificationPreferenceRepository
NotificationInstallationRepository
NotificationDeliveryRepository
NotificationOutboxRepository
```

Mocks:

```text
MockNotificationInboxRepository
MockNotificationPreferenceRepository
MockNotificationInstallationRepository
MockNotificationDeliveryRepository
MockNotificationOutboxRepository
```

No crear implementaciones Supabase.

---

## 6. Categorías

Definir:

```text
ACCOUNT
SECURITY
ORGANIZATION
INVITATION
MODERATION
APPEAL
VERIFICATION
SUPPORT
PET
ADOPTION
FOSTER
SHELTER
LOST_FOUND
DONATION
EVENT
SOCIAL
MESSAGE
SERVICE
APPOINTMENT
PAYMENT
MARKETPLACE
SYSTEM
OTHER
```

Cada categoría debe declarar:

- canales permitidos;
- prioridad por defecto;
- sensibilidad por defecto;
- expiración;
- agrupación;
- si admite lock screen;
- si respeta preferencias;
- si puede exceptuar quiet hours;
- tipo de deep link permitido.

---

## 7. Prioridad y sensibilidad

Prioridad:

```text
LOW
NORMAL
HIGH
URGENT
```

Sensibilidad:

```text
PUBLIC_SUMMARY
PRIVATE
SENSITIVE
SECURITY_CRITICAL
```

Reglas:

- `URGENT` no se usa para engagement.
- `SECURITY_CRITICAL` usa copy genérico.
- `SENSITIVE` no muestra body completo en push.
- unknown → deny.
- prioridad no evita permisos.

---

## 8. Canales

```text
IN_APP
PUSH
EMAIL
LOCAL
```

Reglas:

- IN_APP es fuente de verdad.
- PUSH y EMAIL requieren delivery independiente.
- LOCAL solo para recordatorios aprobados.
- EMAIL no implica marketing.
- Un canal no permitido por categoría debe rechazarse.

---

## 9. Eventos

Modelo mínimo:

```text
eventId
eventKey
category
priority
sensitivity
originModule
originType
resourceType?
resourceId?
organizationId?
occurredAt
expiresAt?
payload
deduplicationKey
idempotencyKey
```

Reglas:

- `eventId`, `eventKey` e `idempotencyKey` obligatorios.
- payload tipado/allowlisted.
- sin tokens, signed URLs, secretos, SQL o stack traces.
- evento expirado no genera delivery.
- origin module allowlisted M01–M05 inicialmente.
- recurso URL arbitrario rechazado.
- INTERNAL usa destinatario staff-only.

---

## 10. Destinatarios

Modelo:

```text
recipientUserId
recipientKind
organizationId?
requiredPermission?
reason
```

Tipos candidatos:

```text
DIRECT_USER
RESOURCE_OWNER
ORGANIZATION_MEMBERS
ORGANIZATION_ROLE
PLATFORM_PERMISSION
RESOURCE_PARTICIPANTS
```

Reglas:

- contratos no aceptan `recipientUserId` arbitrario para eventos server-derived;
- DIRECT_USER solo para eventos permitidos y actor propio;
- organización limitada a M03;
- staff requiere permiso M02;
- requester nunca recibe INTERNAL;
- error → deny.

---

## 11. Preferencias

Modelo:

```text
userId
category
inAppEnabled
pushEnabled
emailEnabled
quietHours?
timezone
marketingConsent
updatedAt
```

Reglas:

- timezone IANA válida;
- marketing OFF por defecto;
- seguridad crítica no totalmente desactivable;
- in-app obligatoria donde corresponda;
- quiet hours pueden cruzar medianoche;
- cambios idempotentes;
- organización no modifica preferencias personales;
- unknown timezone → fallback seguro, no envío inmediato silencioso.

---

## 12. Quiet hours

Modelo:

```text
startLocalTime
endLocalTime
timezone
daysOfWeek
```

Casos:

- mismo día;
- cruza medianoche;
- DST;
- timezone inválida;
- sin configuración;
- excepción SECURITY_CRITICAL.

Resultado:

```text
ALLOW_NOW
DEFER_UNTIL
SKIP
```

No implementar scheduler real.

---

## 13. Instalaciones y tokens

Modelo:

```text
installationId
userId
platform
tokenFingerprint
enabled
appVersion?
deviceLabel?
lastSeenAt
revokedAt?
```

Reglas:

- token raw fuera del modelo público;
- usar fingerprint/hash para igualdad y logs;
- una instalación activa por `installationId`;
- token refresh rota;
- logout actual revoca solo esa instalación;
- otro dispositivo permanece activo;
- usuario cambiado → desvincular instalación anterior;
- token inválido → revocado;
- mock multi-device obligatorio.

---

## 14. Deep links tipados

Modelo:

```text
routeType
resourceType?
resourceId?
organizationId?
requiredPermission?
fallbackRoute
```

Tipos iniciales:

```text
NOTIFICATIONS_INBOX
PROFILE
ORGANIZATION
ORGANIZATION_INVITATION
MODERATION_QUEUE
MODERATION_CASE
MODERATION_APPEAL
ORGANIZATION_VERIFICATION
SUPPORT_TICKET
PET
ADOPTION
LOST_FOUND_CASE
FILE_RESOURCE
CHAT
SAFE_HOME
```

Reglas:

- no aceptar URI/URL arbitraria;
- route allowlist;
- resource ID validado;
- permiso revalidado al abrir;
- organización revalidada;
- staff route requiere permiso;
- recurso ausente → fallback;
- logout limpia navegación pendiente;
- payload push solo lleva IDs mínimos.

---

## 15. Estados

Recipient:

```text
UNREAD
READ
ARCHIVED
DELETED
EXPIRED
```

Delivery:

```text
PENDING
PROCESSING
DELIVERED
FAILED_RETRYABLE
FAILED_PERMANENT
SKIPPED_PREFERENCE
SKIPPED_PERMISSION
SKIPPED_EXPIRED
SKIPPED_QUIET_HOURS
CANCELLED
DEAD_LETTER
```

Reglas:

- entrega push no marca READ;
- archive no elimina;
- deleted es lógico;
- expirado no se entrega;
- transición inválida → deny.

---

## 16. Deduplicación e idempotencia

Modelo:

```text
deduplicationKey
idempotencyKey
window
recipientScope
```

Reglas:

- mismo evento + destinatario no duplica bandeja;
- reintento conserva el mismo registro;
- varios dispositivos generan deliveries distintas, no recipients duplicados;
- keys vacías rechazadas;
- ventana explícita;
- agrupación no elimina trazabilidad.

---

## 17. Retry y dead-letter

Política:

```text
maxAttempts
initialDelay
maxDelay
multiplier
retryableFailureCodes
```

Reglas:

- backoff acotado;
- solo errores transitorios;
- token inválido es fallo permanente + revoke;
- expirado se salta;
- tras máximo → dead-letter;
- reproceso manual futuro con auditoría;
- no scheduler real en Etapa 2.

---

## 18. Outbox

Contrato:

```text
NotificationOutboxEvent
NotificationOutboxState
NotificationOutboxRepository
```

Estados:

```text
PENDING
CLAIMED
PROCESSED
FAILED_RETRYABLE
FAILED_PERMANENT
DEAD
CANCELLED
```

Reglas:

- idempotency key obligatoria;
- recipient se calcula server-side futuro;
- Etapa 2 solo contrato/mock;
- no Edge Function;
- no cron;
- no SQL;
- no emitir push real.

---

## 19. Compatibilidad legacy

Definir adaptadores de lectura:

```text
LegacyNotificationTypeAdapter
LegacyNotificationReference
LegacyNotificationDeepLinkAdapter
```

Reglas:

- tipos actuales FRIEND_REQUEST, CHAT_MESSAGE, etc. se mapean a categorías nuevas;
- lectura compatible;
- no generar nuevos eventos inseguros;
- legacy no concede permisos;
- payload legacy desconocido → SAFE_HOME;
- `NotificationDispatcher` queda intacto en Etapa 2.

---

## 20. Autorización

Resultado explícito:

```text
ALLOWED
DENIED_NOT_AUTHENTICATED
DENIED_RECIPIENT
DENIED_PERMISSION
DENIED_ORGANIZATION
DENIED_SENSITIVITY
DENIED_INTERNAL
DENIED_PREFERENCE
DENIED_EXPIRED
DENIED_DEEP_LINK
DENIED_UNKNOWN
```

Reglas:

- deny-by-default;
- notificación no concede acceso;
- INTERNAL staff-only;
- M03 limitado a su organización;
- M02 para staff global;
- AccountType/modules sin autoridad;
- permiso desconocido → deny.

---

## 21. Repositorios

### Inbox

```text
listNotifications
getUnreadCount
markRead
markAllRead
archive
deleteLogical
```

### Preferencias

```text
getPreferences
updatePreference
getEffectiveChannels
```

### Instalaciones

```text
registerInstallation
rotateToken
revokeCurrentInstallation
listOwnInstallations
```

### Delivery

```text
planDeliveries
recordAttempt
markDelivered
markFailure
```

### Outbox

```text
enqueueEvent
claimNext
markProcessed
markFailed
moveToDeadLetter
```

En Etapa 2:

- interfaces;
- mocks deterministas;
- AppResult/AppError;
- sin Supabase;
- sin FCM real.

---

## 22. DataProvider

Para `useSupabase = false`:

- mocks M06 no nulos;
- multi-device;
- inbox única por usuario;
- tokens simulados mediante fingerprints;
- preferencias configurables;
- quiet hours con reloj inyectable;
- fallos y retry configurables.

Para `useSupabase = true`:

- mantener repositorios legacy actuales sin reemplazo;
- no cablear implementaciones nuevas aún;
- no romper bandeja ni FCM existentes.

---

## 23. Pruebas obligatorias

Crear pruebas equivalentes a:

```text
NotificationCategoryPolicyTest
NotificationSensitivityRulesTest
NotificationEventRulesTest
NotificationRecipientRulesTest
NotificationPreferenceRulesTest
NotificationQuietHoursTest
NotificationInstallationRulesTest
NotificationDeepLinkRulesTest
NotificationStateTransitionTest
NotificationDeduplicationTest
NotificationRetryPolicyTest
NotificationOutboxRulesTest
NotificationAuthorizationTest
NotificationLegacyCompatibilityTest
NotificationRepositoryMocksTest
```

Casos mínimos:

- INTERNAL a requester;
- sensible con body push;
- route arbitraria;
- org incorrecta;
- permiso desconocido;
- marketing sin consentimiento;
- quiet hours cruzando medianoche;
- SECURITY_CRITICAL exception;
- logout revoca solo instalación actual;
- múltiples dispositivos;
- duplicate recipient;
- retry idempotente;
- dead-letter;
- token en logs/modelo;
- signed URL en payload;
- legacy unknown → SAFE_HOME;
- AccountType/modules no autorizan.

Conservar las 358 pruebas existentes.

---

## 24. Calidad

Ejecutar:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Documentar total final.

---

## 25. Documento de cierre

Crear exactamente:

```text
/docs/02-arquitectura/M06-etapa-2-cierre.md
```

Debe incluir:

- rama;
- commit base;
- archivos;
- decisiones;
- categorías;
- prioridades;
- sensibilidad;
- canales;
- eventos;
- destinatarios;
- preferencias;
- quiet hours;
- instalaciones;
- deep links;
- estados;
- dedup;
- retry/dead-letter;
- outbox;
- compatibilidad legacy;
- autorización;
- repositorios y mocks;
- DataProvider;
- pruebas;
- build/lint;
- deuda;
- staging;
- parada.

---

## 26. Criterios de aceptación

- [ ] Working tree limpio.
- [ ] Rama correcta.
- [ ] Sin SQL/migraciones.
- [ ] Sin Firebase nuevo.
- [ ] Sin Edge Functions/jobs/cron/WorkManager.
- [ ] Sin pantallas.
- [ ] Sin implementaciones Supabase nuevas.
- [ ] Categorías y políticas centralizadas.
- [ ] Sensibilidad y prioridad centralizadas.
- [ ] Eventos tipados.
- [ ] Destinatarios tipados.
- [ ] Preferencias y quiet hours.
- [ ] Instalaciones multi-device.
- [ ] Logout actual revoca solo instalación actual en contrato/mock.
- [ ] Deep links allowlisted.
- [ ] INTERNAL protegido.
- [ ] Dedup e idempotencia.
- [ ] Retry/dead-letter.
- [ ] Outbox contrato/mock.
- [ ] Legacy compatible.
- [ ] AccountType/modules sin autoridad.
- [ ] M03 scoped a org.
- [ ] Tests verdes.
- [ ] Build/lint verdes.
- [ ] Cierre creado.
- [ ] Sin Etapa 3.
- [ ] Sin M07.
- [ ] Sin merge a main.

---

## 27. Parada

No iniciar Etapa 3.

No corregir todavía `create_notification`, la policy INSERT ni `NotificationDispatcher`.

No crear migraciones.

No iniciar M07.

No hacer merge a `main`.

Detenerse al crear:

```text
/docs/02-arquitectura/M06-etapa-2-cierre.md
```
