# M06 — Cierre Etapa 2: Contratos, preferencias, deep links e instalaciones

**Fecha:** 2026-07-16  
**Rama:** `m06/etapa-2-contratos-preferencias-deep-links-instalaciones`  
**Módulo:** M06 — Notificaciones  
**Producto:** LeoVer  
**Commit base:** `ee677373beec7311b029c4c7b90f4a35d7a8785a`  
**Estado de entrada:** Etapa 1 revisada, aprobada y consolidada  
**Spec:** `docs/03-modulos/M06-Etapa-2-Contratos-Categorias-Preferencias-Deep-Links-e-Instalaciones.md`

---

## 1. Protección Git y alcance

| Control | Resultado |
|---------|-----------|
| Commit base | Confirmado |
| Working tree inicial | Limpio salvo spec Etapa 2 untracked |
| Rama | `m06/etapa-2-contratos-preferencias-deep-links-instalaciones` |
| Merge a `main` | **No** |
| Etapa 3 / M07 | **No** iniciados |
| GPS / mapas / pagos | **No** incorporados |
| Producción / staging | **No** usados |
| SQL / migraciones / RLS / grants / policies / RPC | **Sin cambios** |
| `create_notification` / INSERT `notifications` | **Sin cambios** (deuda Etapa 3) |
| `NotificationDispatcher` | **Sin cambios** |
| Firebase / Edge Functions / jobs / cron / WorkManager | **Sin cambios** |
| Pantallas / repositorios Supabase | **No** creados |
| AuthRepository / `domain/auth` / username | **Sin cambios** |

---

## 2. Archivos

### Creados — dominio

Directorio `app/src/main/java/com/comunidapp/app/domain/notifications/`:

- `NotificationCategory.kt`
- `NotificationPriority.kt`
- `NotificationSensitivity.kt`
- `NotificationChannel.kt`
- `NotificationState.kt`
- `NotificationDeliveryState.kt`
- `NotificationEvent.kt`
- `NotificationRecipient.kt`
- `NotificationPreference.kt`
- `NotificationQuietHours.kt`
- `NotificationInstallation.kt`
- `NotificationDeepLink.kt`
- `NotificationDeduplication.kt`
- `NotificationRetryPolicy.kt`
- `NotificationOutbox.kt`
- `NotificationContracts.kt`

Autorización:

- `authorization/NotificationAuthorization.kt`
- `authorization/NotificationVisibilityRules.kt`
- `authorization/NotificationDeepLinkAuthorization.kt`

### Creados — datos

- `data/repository/NotificationRepositories.kt`
- `data/notifications/LegacyNotificationAdapters.kt`

### Modificado

- `data/provider/DataProvider.kt` — repositorios mock M06 no nulos; comportamiento legacy Supabase/FCM intacto.

### Pruebas creadas

- `NotificationCategoryPolicyTest`
- `NotificationSensitivityRulesTest`
- `NotificationEventRulesTest`
- `NotificationRecipientRulesTest`
- `NotificationPreferenceRulesTest`
- `NotificationQuietHoursTest`
- `NotificationInstallationRulesTest`
- `NotificationDeepLinkRulesTest`
- `NotificationStateTransitionTest`
- `NotificationDeduplicationTest`
- `NotificationRetryPolicyTest`
- `NotificationOutboxRulesTest`
- `NotificationAuthorizationTest`
- `NotificationLegacyCompatibilityTest`
- `NotificationRepositoryMocksTest`

---

## 3. Decisiones y contratos

### 3.1 Fuente de verdad y deliveries

- La bandeja `IN_APP` es la fuente de verdad.
- `PUSH` y `EMAIL` son deliveries independientes.
- Entregar un push no cambia el estado del recipient a `READ`.
- Varias instalaciones generan deliveries distintas, pero una sola entrada in-app por usuario y clave de deduplicación.
- Los contratos no permiten que Android derive destinatarios arbitrarios; el cálculo real será server-side en Etapa 3.

### 3.2 Categorías

Se definieron exactamente 23 categorías:

```text
ACCOUNT, SECURITY, ORGANIZATION, INVITATION, MODERATION, APPEAL,
VERIFICATION, SUPPORT, PET, ADOPTION, FOSTER, SHELTER, LOST_FOUND,
DONATION, EVENT, SOCIAL, MESSAGE, SERVICE, APPOINTMENT, PAYMENT,
MARKETPLACE, SYSTEM, OTHER
```

`NotificationCategoryPolicies` centraliza por categoría:

- canales permitidos;
- prioridad y sensibilidad por defecto;
- expiración;
- agrupación;
- lock screen;
- respeto de preferencias;
- excepción de quiet hours;
- deep links permitidos;
- obligatoriedad in-app;
- consentimiento de marketing.

### 3.3 Prioridad

```text
LOW, NORMAL, HIGH, URGENT
```

`URGENT` queda restringida a categorías aprobadas; no es una vía para evitar permisos, preferencias o sensibilidad.

### 3.4 Sensibilidad

```text
PUBLIC_SUMMARY, PRIVATE, SENSITIVE, SECURITY_CRITICAL
```

- `SENSITIVE` y `SECURITY_CRITICAL` requieren copy genérico para push/lock screen.
- Payloads rechazan tokens, signed URLs, secretos, SQL, stack traces, URLs arbitrarias y claves de PII completa.
- INTERNAL no puede ser público ni dirigirse al requester.

### 3.5 Canales

```text
IN_APP, PUSH, EMAIL, LOCAL
```

- Un canal no permitido por la política de categoría se rechaza.
- `LOCAL` queda solo como contrato para recordatorios aprobados; no se creó scheduler.
- Marketing requiere consentimiento explícito y queda OFF por defecto.

---

## 4. Eventos y destinatarios

### Eventos

`NotificationEvent` incluye:

- identidad (`eventId`, `eventKey`);
- categoría, prioridad y sensibilidad;
- origen allowlisted M01–M05;
- recurso y organización tipados;
- tiempos y expiración;
- payload allowlisted;
- `deduplicationKey`;
- `idempotencyKey`;
- flag INTERNAL.

Evento expirado no genera delivery. Recurso URL arbitrario y payload sensible se rechazan.

### Destinatarios

Tipos:

```text
DIRECT_USER, RESOURCE_OWNER, ORGANIZATION_MEMBERS, ORGANIZATION_ROLE,
PLATFORM_PERMISSION, RESOURCE_PARTICIPANTS
```

- `DIRECT_USER` se niega por defecto para eventos server-derived.
- Organización queda limitada al contexto M03.
- Staff requiere permiso conocido.
- INTERNAL es exclusivamente staff-only.
- El requester nunca recibe notas ni adjuntos INTERNAL.

---

## 5. Preferencias y quiet hours

`NotificationPreference` modela preferencias por usuario/categoría:

- in-app;
- push;
- email;
- quiet hours;
- timezone IANA;
- consentimiento de marketing;
- actualización.

Reglas:

- in-app puede ser obligatorio para seguridad/legal/administración;
- marketing OFF por defecto;
- organización no modifica preferencias personales;
- timezone inválida usa fallback seguro marcado como no válido;
- push/email respetan preferencias.

`NotificationQuietHours` soporta:

- ventana del mismo día;
- cruce de medianoche;
- días de semana;
- zona IANA y DST vía `ZoneId`;
- `ALLOW_NOW`, `DEFER_UNTIL`, `SKIP`;
- excepción SECURITY_CRITICAL únicamente con política y flag explícitos.

No existe scheduler real.

---

## 6. Instalaciones

`NotificationInstallation` representa una instalación, no un usuario:

- `installationId`;
- `userId`;
- plataforma;
- fingerprint de token (sin token raw);
- enabled;
- versión/label;
- last seen;
- revocación.

Reglas:

- token raw no forma parte del modelo público;
- refresh rota el fingerprint;
- logout revoca solo la instalación actual;
- otros dispositivos permanecen activos;
- cambio de cuenta desvincula la instalación anterior y registra la nueva;
- múltiples dispositivos están soportados en mocks;
- token inválido se clasifica como fallo permanente y revocación futura.

---

## 7. Deep links tipados

Allowlist inicial:

```text
NOTIFICATIONS_INBOX, PROFILE, ORGANIZATION, ORGANIZATION_INVITATION,
MODERATION_QUEUE, MODERATION_CASE, MODERATION_APPEAL,
ORGANIZATION_VERIFICATION, SUPPORT_TICKET, PET, ADOPTION,
LOST_FOUND_CASE, FILE_RESOURCE, CHAT, SAFE_HOME
```

Reglas:

- no URI/URL arbitraria;
- campos de recurso/organización validados;
- rutas staff requieren permiso;
- organización se revalida;
- al abrir se revalidan sesión, recurso, permiso y organización;
- notificación nunca concede acceso;
- recurso ausente o payload desconocido → `SAFE_HOME`.

---

## 8. Estados

Recipient:

```text
UNREAD, READ, ARCHIVED, DELETED, EXPIRED
```

Delivery:

```text
PENDING, PROCESSING, DELIVERED, FAILED_RETRYABLE, FAILED_PERMANENT,
SKIPPED_PREFERENCE, SKIPPED_PERMISSION, SKIPPED_EXPIRED,
SKIPPED_QUIET_HOURS, CANCELLED, DEAD_LETTER
```

Las transiciones están centralizadas y las inválidas se deniegan. Archive y delete son lógicos; entregar push no marca leído.

---

## 9. Deduplicación e idempotencia

`NotificationDeduplication` define:

- `deduplicationKey`;
- `idempotencyKey`;
- recipient scope;
- ventana temporal.

Reglas:

- claves vacías se rechazan;
- mismo evento + usuario no duplica bandeja;
- varios dispositivos crean deliveries distintas;
- reintentos preservan identidad;
- agrupación no elimina trazabilidad.

---

## 10. Retry y dead-letter

`NotificationRetryPolicy` define:

- máximo de intentos;
- delay inicial y máximo;
- multiplicador;
- códigos transitorios.

El backoff exponencial queda acotado. Fallos:

- transitorio → retry hasta máximo;
- permanente → no retry;
- token inválido → permanente;
- expirado → skip;
- máximo agotado → dead-letter.

No se creó scheduler, job ni WorkManager.

---

## 11. Outbox

Estados:

```text
PENDING, CLAIMED, PROCESSED, FAILED_RETRYABLE,
FAILED_PERMANENT, DEAD, CANCELLED
```

Contrato/mock implementa:

- enqueue idempotente;
- claim;
- processed;
- retryable;
- permanent failure;
- dead-letter;
- `idempotencyKey` obligatoria.

No hay SQL, Edge Function, cron ni entrega real.

---

## 12. Autorización

Decisiones explícitas deny-by-default:

```text
ALLOWED, DENIED_NOT_AUTHENTICATED, DENIED_RECIPIENT,
DENIED_PERMISSION, DENIED_ORGANIZATION, DENIED_SENSITIVITY,
DENIED_INTERNAL, DENIED_PREFERENCE, DENIED_EXPIRED,
DENIED_DEEP_LINK, DENIED_UNKNOWN
```

Confirmaciones:

- la notificación no concede acceso;
- INTERNAL staff-only;
- M03 limitado a la organización;
- staff global usa permisos M02 conocidos;
- permiso desconocido/error → deny;
- AccountType y `active_modules` no forman parte del contexto y nunca conceden autoridad.

---

## 13. Compatibilidad legacy

`LegacyNotificationTypeAdapter` mapea:

```text
FRIEND_REQUEST, FRIEND_ACCEPTED, CHAT_MESSAGE, ADOPTION_REQUEST,
FOSTER_REQUEST, BOOKING, SIGHTING, SYSTEM
```

- lectura compatible con `AppNotification`;
- tipo/payload desconocido → `SAFE_HOME`;
- legacy no concede permisos;
- legacy no genera eventos nuevos;
- `NotificationDispatcher` permanece intacto.

---

## 14. Repositorios y mocks

Interfaces:

- `NotificationInboxRepository`;
- `NotificationPreferenceRepository`;
- `NotificationInstallationRepository`;
- `NotificationDeliveryRepository`;
- `NotificationOutboxRepository`.

Mocks deterministas:

- store compartido;
- IDs predecibles;
- inbox única por usuario;
- preferencias configurables;
- instalaciones multi-device;
- fingerprints simulados, sin tokens reales;
- reloj y retry inyectables;
- deduplicación/idempotencia;
- outbox y dead-letter.

---

## 15. DataProvider

- `useSupabase=false`: mocks M06 no nulos.
- `useSupabase=true`: conserva `PlatformRepository`, bandeja y FCM legacy; los contratos nuevos permanecen mocks hasta Etapa 3.
- No se creó implementación Supabase M06.
- Se exponen inbox, preferencias, instalaciones, deliveries y outbox de contrato.

---

## 16. Pruebas y calidad

| Control | Resultado |
|---------|-----------|
| Suite unitaria | **431** tests, **0** failures, **0** errors |
| Pruebas previas conservadas | **358** |
| Nuevas M06 Etapa 2 | **73** |
| `assembleDebug` | **SUCCESS** |
| `testDebugUnitTest` | **SUCCESS** |
| `lintDebug` | **SUCCESS** |

Casos críticos cubiertos:

- INTERNAL al requester;
- sensible con body push;
- ruta arbitraria;
- organización incorrecta;
- permiso desconocido;
- marketing sin consentimiento;
- quiet hours cruzando medianoche;
- excepción SECURITY_CRITICAL;
- revocación de instalación actual y multi-device;
- destinatario duplicado;
- retry idempotente / dead-letter;
- token raw y signed URL rechazados;
- legacy desconocido → SAFE_HOME;
- AccountType/modules sin autoridad.

---

## 17. Deuda pendiente

| Ítem | Etapa |
|------|-------|
| Endurecer `create_notification` e INSERT abierta | **Etapa 3** |
| Persistencia SQL/RLS/outbox/recipients | Etapa 3 |
| Implementaciones Supabase M06 | Etapa 3 |
| Cálculo real server-side de destinatarios | Etapa 3 |
| Realtime/in-app persistente | Etapa 3 |
| Push gobernado y deep links operativos | Etapa 4 |
| Revocación real por instalación en FCM legacy | Etapa 4 |
| Observabilidad coordinada con M07 | Etapa 5 / M07 |
| Staging `014`–`025` | **PENDIENTE DE VALIDACIÓN REMOTA** |

La deuda crítica de `create_notification` sigue visible y no fue corregida por alcance explícito.

---

## 18. Checklist

- [x] Base y rama correctas
- [x] Sin SQL/migraciones/RLS/RPC
- [x] Sin Firebase/Edge Functions/jobs/cron/WorkManager
- [x] Sin pantallas ni Supabase repos nuevos
- [x] Categorías/políticas centralizadas
- [x] Eventos/destinatarios tipados
- [x] Preferencias/quiet hours
- [x] Instalaciones multi-device y current-only revoke
- [x] Deep links allowlisted
- [x] INTERNAL protegido
- [x] Dedup/idempotencia/retry/dead-letter/outbox
- [x] Autorización deny-by-default
- [x] Legacy compatible
- [x] Mocks + DataProvider
- [x] 431 tests / build / lint
- [x] Auth/username intactos
- [x] Sin Etapa 3 / M07 / merge a main

---

## 19. Parada

Etapa 2 cerrada a nivel de contratos locales y mocks.

**No** iniciar Etapa 3.  
**No** corregir todavía `create_notification`, la policy INSERT ni `NotificationDispatcher`.  
**No** crear migraciones.  
**No** iniciar M07.  
**No** merge a `main`.  
Cambios **sin commit**, listos para revisión.
