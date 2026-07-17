# LEOVER — M06 Etapa 3: Persistencia, Seguridad, Outbox y Entrega In-App

**Módulo:** M06 — Notificaciones  
**Etapa:** 3 — Persistencia, seguridad, outbox y entrega in-app server-side  
**Estado de entrada:** Etapa 2 aprobada y consolidada  
**Commit base:** `c38fb3aaecf34badee1478e7388d851f60f4165c`  
**Rama base:** `m06/etapa-2-contratos-preferencias-deep-links-instalaciones`  
**Calidad de entrada:** 431 tests, 0 failures, 0 errors; `assembleDebug` y `lintDebug` en SUCCESS  
**Staging heredado:** migraciones `014`–`025` pendientes de validación remota  
**Objetivo central:** cerrar el riesgo crítico que permite a un cliente autenticado crear notificaciones para terceros y establecer una fuente de verdad in-app server-side, idempotente y compatible con datos legacy.  
**Fuera de alcance:** ampliación de FCM, nuevos canales Android, push gobernado, email, WorkManager, cron productivo, M07, producción y cambios de username/auth.

---

## 1. Documentos obligatorios

Leer en este orden:

1. `/docs/02-arquitectura/M06-auditoria-inicial.md`
2. `/docs/02-arquitectura/M06-etapa-2-cierre.md`
3. `/docs/03-modulos/M06-Notificaciones.md`
4. `/docs/03-modulos/M06-Etapa-2-Contratos-Categorias-Preferencias-Deep-Links-e-Instalaciones.md`
5. `/docs/02-arquitectura/M05-cierre-final.md`
6. `/docs/04-calidad/M05-reporte-validacion-staging.md`
7. ADR-0001 a ADR-0005
8. Este documento.

---

## 2. Protección Git

1. Confirmar el commit base:

```text
c38fb3aaecf34badee1478e7388d851f60f4165c
```

2. Confirmar working tree limpio.
3. Crear la rama:

```text
m06/etapa-3-persistencia-seguridad-outbox-in-app
```

4. No incorporar WIP GPS/mapas/pagos.
5. No hacer merge a `main`.
6. No iniciar Etapa 4 ni M07.
7. No aplicar producción.
8. No modificar username, `AuthRepository`, `domain/auth` ni validadores.
9. No afirmar staging sin evidencia.

---

## 3. Decisión de arquitectura

### 3.1 Compatibilidad con la tabla legacy

Conservar `public.notifications` de la migración `012` como tabla de bandeja por usuario para evitar una ruptura masiva.

Evolucionarla mediante una migración nueva; no editar `012`.

Agregar, cuando no existan:

```text
event_id
category
priority
sensitivity
state
read_at
archived_at
deleted_at
expires_at
organization_id
deep_link_type
deep_link_resource_type
deep_link_resource_id
deep_link_required_permission
payload
deduplication_key
created_by
```

Reglas:

- filas legacy continúan siendo legibles;
- defaults seguros para filas antiguas;
- `is_read` legacy y `state/read_at` deben permanecer sincronizados durante la transición;
- no migrar ni borrar datos sin estrategia reversible;
- payload legacy desconocido usa `SAFE_HOME`;
- la compatibilidad no mantiene abierta la creación insegura desde Android.

### 3.2 Evento canónico y outbox

Crear tablas equivalentes a:

```text
notification_events
notification_preferences
notification_device_installations
notification_deliveries
notification_outbox
notification_dead_letters
notification_access_audit
```

La tabla `notifications` representa la entrada in-app del destinatario.

No duplicar la bandeja por dispositivo.

---

## 4. Migración

Crear una única migración nueva, salvo defecto real durante la ejecución:

```text
supabase/migrations/026_m06_notifications_security_outbox_foundation.sql
```

No editar migraciones `001`–`025`.

La migración debe ser:

- idempotente donde sea razonable;
- transaccional;
- compatible con datos existentes;
- deny-by-default;
- documentada;
- sin secretos;
- sin FCM server key;
- sin service role en Android.

---

## 5. Cierre del riesgo crítico

### 5.1 INSERT directo

Eliminar o reemplazar la policy insegura equivalente a:

```text
WITH CHECK (true)
```

Resultado obligatorio:

- un usuario autenticado no puede insertar directamente una notificación para otro usuario;
- tampoco puede insertar libremente una notificación para sí mismo cuando el evento deba ser server-derived;
- Android no tiene grant para escribir arbitrariamente eventos, outbox, deliveries o dead letters.

### 5.2 `create_notification`

Reemplazar o endurecer la función existente mediante `026`.

Reglas:

- `SECURITY DEFINER`;
- `search_path = public`;
- actor desde `auth.uid()`;
- denegar destinatario arbitrario;
- no confiar en rol, categoría, sensibilidad, organización o permiso enviados por Android;
- no aceptar URL/deep link arbitrario;
- no aceptar signed URL, token, secreto, SQL, stack trace o PII completa;
- idempotency y dedup obligatorios para caminos server-side;
- uso cliente legado cross-user debe quedar denegado.

Compatibilidad permitida:

- conservar una variante self-only estricta únicamente para eventos explícitamente allowlisted y no sensibles, si es imprescindible para no romper una operación propia;
- debe estar documentada y probada;
- no puede usarse para chat, amistad, adopción, moderación, soporte, organización o cualquier destinatario tercero.

### 5.3 `NotificationDispatcher`

No eliminarlo sin reemplazo.

En Etapa 3:

- impedir que sea una vía válida para crear notificaciones cross-user;
- adaptar llamadas existentes para que fallen de forma segura y observable, o desactivar su envío inseguro;
- no simular éxito;
- no introducir push nuevo;
- documentar qué flujos quedan temporalmente sin aviso hasta su migración server-side.

---

## 6. Modelo mínimo

### 6.1 `notification_events`

Campos mínimos:

```text
id uuid
event_key text
origin_module text
origin_type text
category text
priority text
sensitivity text
resource_type text?
resource_id uuid/text?
organization_id uuid?
payload jsonb
deduplication_key text
idempotency_key text
occurred_at timestamptz
expires_at timestamptz?
created_by uuid?
created_at timestamptz
```

Constraints:

- categorías, prioridad y sensibilidad allowlisted;
- payload objeto JSON con tamaño acotado;
- keys no vacías;
- expiración posterior a ocurrencia;
- no signed URLs/tokens mediante validadores server-side razonables;
- unique de idempotency;
- dedup por evento/destinatario/ventana en la materialización de inbox.

### 6.2 `notification_preferences`

```text
user_id
category
in_app_enabled
push_enabled
email_enabled
marketing_consent
quiet_hours_start
quiet_hours_end
quiet_hours_days
timezone
updated_at
```

Reglas:

- usuario solo lee/modifica las propias;
- categorías obligatorias no permiten desactivar in-app;
- marketing OFF por defecto;
- timezone validada de forma segura;
- cambio auditado sin contenido sensible.

### 6.3 `notification_device_installations`

```text
id
installation_id
user_id
platform
token_protected_or_token_reference
token_fingerprint
enabled
app_version
device_label
last_seen_at
revoked_at
created_at
updated_at
```

Reglas:

- usuario solo administra instalaciones propias;
- raw token nunca se devuelve en listados;
- logout futuro revoca instalación actual;
- no borrar todas las instalaciones del usuario;
- unique por instalación;
- rotación idempotente;
- tabla `device_tokens` legacy permanece sin borrar en esta etapa, con estrategia de coexistencia documentada.

### 6.4 `notification_deliveries`

```text
id
notification_id
channel
installation_id?
status
attempt_count
next_attempt_at?
last_attempt_at?
delivered_at?
failure_code?
provider_message_id?
created_at
updated_at
```

Android no modifica deliveries.

### 6.5 `notification_outbox`

```text
id
event_id
state
attempt_count
available_at
claimed_at?
claimed_by?
last_error_code?
created_at
updated_at
```

Reglas:

- enqueue idempotente;
- claim atómico;
- backoff acotado;
- expirado no procesa;
- máximo agotado → dead letter;
- Android no encola eventos arbitrarios.

### 6.6 `notification_dead_letters`

Conservar:

- evento;
- error sanitizado;
- intentos;
- timestamp;
- estado de reproceso;
- sin tokens ni payload sensible completo.

---

## 7. Funciones server-side

Crear funciones equivalentes, con nombres consistentes con el proyecto:

```text
m06_require_active_actor()
m06_validate_payload(...)
m06_can_read_notification(...)
m06_enqueue_domain_event(...)
m06_materialize_in_app_notification(...)
m06_claim_outbox(...)
m06_mark_outbox_processed(...)
m06_mark_outbox_failed(...)
m06_get_inbox(...)
m06_get_unread_count()
m06_mark_notification_read(...)
m06_mark_all_notifications_read(...)
m06_archive_notification(...)
m06_delete_notification_logical(...)
m06_get_preferences(...)
m06_update_preference(...)
m06_register_installation(...)
m06_rotate_installation_token(...)
m06_revoke_current_installation(...)
```

Reglas:

- `SECURITY DEFINER` solo donde sea necesario;
- `search_path = public`;
- actor `auth.uid()`;
- recipient calculado server-side;
- grants mínimos;
- funciones internas sin grant a `authenticated`;
- respuestas sin SQL, stack, token o path sensible;
- estados y transiciones validados;
- mark/read/archive/delete solo para el destinatario.

No crear worker externo ni cron productivo en esta etapa.

---

## 8. Eventos server-side M01–M05

Implementar un subconjunto seguro y verificable basado en tablas/funciones existentes.

Prioridad mínima:

### M03

- invitación creada;
- invitación aceptada/rechazada/expirada;
- cambio de rol;
- remoción de miembro;
- transferencia de ownership, si existe flujo persistido.

### M04

- acción de moderación aplicada;
- apelación presentada/resuelta;
- verificación con `MORE_INFO`, `APPROVED`, `REJECTED` o `REVOKED`;
- ticket de soporte creado;
- respuesta visible al solicitante;
- actualización staff-only para mensajes INTERNAL.

### M05

- upload completado;
- upload fallido/cancelado cuando exista estado persistido;
- documento de verificación disponible para staff autorizado.

### M01/M02

Solo eventos que puedan derivarse con seguridad del backend existente, sin modificar autenticación:

- cambio de estado de cuenta;
- rol o permiso crítico;
- solicitud de eliminación.

Reglas:

- no inventar eventos si no existe una fuente transaccional segura;
- documentar cada evento IMPLEMENTADO, PARCIAL o PENDIENTE;
- INTERNAL jamás se materializa para requester;
- org events respetan scope M03;
- staff requiere permiso M02/M04;
- evento y notificación se crean una sola vez;
- preferir triggers o integración dentro de RPCs de dominio;
- no usar llamadas desde ViewModel para calcular recipient.

---

## 9. RLS y grants

### `notifications`

- SELECT solo destinatario;
- UPDATE solo transiciones permitidas propias;
- sin INSERT/DELETE físico cliente;
- staff no obtiene acceso global por AccountType;
- datos INTERNAL solo destinatario staff calculado.

### Preferencias e instalaciones

- usuario solo sobre sus filas;
- actualización mediante RPC validada cuando haga falta;
- raw token no visible;
- revocación por instalación actual.

### Eventos, outbox, deliveries y dead letters

- sin escritura desde Android;
- lectura denegada por defecto;
- acceso staff solo si existe una necesidad documentada y permiso explícito;
- no conceder acceso global a roles organizacionales.

---

## 10. Android y repositorios Supabase

Crear implementaciones reales para:

```text
SupabaseNotificationInboxRepository
SupabaseNotificationPreferenceRepository
SupabaseNotificationInstallationRepository
```

No crear capacidad cliente para:

- encolar eventos arbitrarios;
- elegir recipients;
- escribir outbox;
- marcar deliveries;
- mover dead letters.

Para `NotificationDeliveryRepository` y `NotificationOutboxRepository`:

- mantener mocks para pruebas de dominio;
- en `useSupabase=true`, usar una implementación client-denied o no exponer mutaciones peligrosas;
- documentar que son contratos server-side.

### Inbox

Integrar la pantalla existente:

- listar nueva bandeja;
- unread;
- mark read;
- mark all;
- archive;
- delete lógico;
- compatibilidad legacy;
- expiradas no contadas como unread;
- estados seguros;
- sin cambio visual masivo.

### Preferencias

Persistencia real sin crear todavía una pantalla completa.

### Instalaciones

Preparar repositorio real:

- registrar instalación;
- rotar token;
- revocar instalación actual;
- no borrar todos los dispositivos;
- convivencia con `device_tokens` legacy;
- no modificar aún el servicio FCM más allá de lo necesario para no exponer el riesgo.

---

## 11. Realtime

Elegir una opción y documentarla:

### Opción preferida

Agregar `notifications` a Realtime de forma controlada y mantener fallback de refresh manual.

Requisitos:

- solo filas autorizadas por RLS;
- sin exponer eventos/outbox/deliveries;
- evitar duplicar listeners;
- cerrar suscripción en logout/cambio de cuenta;
- tests donde sea posible.

Si no puede validarse localmente:

- dejar repositorio listo;
- documentar staging pendiente;
- no afirmar Realtime operativo.

---

## 12. Compatibilidad legacy

- conservar lectura de filas `012`;
- mapear tipos legacy;
- sincronizar `is_read` y estado nuevo;
- no borrar `device_tokens`;
- no romper pantalla existente;
- `NotificationDispatcher` no puede crear cross-user;
- eventos legacy desconocidos → `SAFE_HOME`;
- documentar flujos temporalmente sin notificación;
- no duplicar notificaciones legacy y nuevas.

---

## 13. Errores y auditoría

Errores Android:

```text
NOTIFICATION_BACKEND_NOT_READY
NOTIFICATION_PERMISSION_DENIED
NOTIFICATION_NOT_FOUND
NOTIFICATION_ALREADY_PROCESSED
NOTIFICATION_EXPIRED
NOTIFICATION_INVALID_DEEP_LINK
NOTIFICATION_INTERNAL_DENIED
NOTIFICATION_INSTALLATION_REVOKED
NOTIFICATION_RETRYABLE
NOTIFICATION_UNKNOWN
```

No exponer:

- SQL;
- stack trace;
- token;
- provider message ID;
- payload sensible;
- recipient interno;
- detalles de policies.

Auditar:

- evento materializado;
- recipient calculado;
- preferencia aplicada;
- mark read/archive/delete;
- instalación registrada/revocada;
- outbox procesada/fallida;
- sin PII completa.

M07 sigue siendo el propietario transversal.

---

## 14. Pruebas obligatorias

Conservar las 431 pruebas existentes.

Agregar pruebas SQL/estáticas y Kotlin para:

### Seguridad crítica

- usuario A no crea notificación para B;
- INSERT directo denegado;
- `create_notification` cross-user denegado;
- self-only, si existe, limitado a allowlist;
- Android no encola outbox;
- Android no escribe deliveries;
- AccountType/modules sin autoridad;
- organización incorrecta;
- permiso desconocido;
- INTERNAL no al requester;
- payload con token/signed URL rechazado.

### Persistencia

- idempotency unique;
- dedup recipient;
- mismo evento no duplica inbox;
- varios dispositivos no duplican inbox;
- legacy row legible;
- `is_read` sincronizado;
- expiradas fuera de unread;
- archive/delete lógico;
- mark all solo propias.

### Preferencias

- seguridad obligatoria in-app;
- marketing OFF;
- quiet hours persistidas;
- otro usuario no modifica preferencias;
- canal no permitido denegado.

### Instalaciones

- registrar;
- rotar;
- revocar actual;
- otro dispositivo sigue activo;
- raw token no listado;
- cambio de usuario;
- device_tokens legacy no borrado.

### Outbox

- enqueue idempotente;
- claim atómico;
- retry;
- dead-letter;
- expiración;
- Android sin grants.

### Eventos M01–M05

- destinatarios correctos;
- organización correcta;
- permisos staff;
- INTERNAL staff-only;
- no duplicación;
- evento inexistente no inventado.

### Regresión

- bandeja existente;
- FCM legacy no ampliado;
- M01–M05;
- auth/username intactos;
- M05 file cleanup;
- WIP aislado.

---

## 15. Calidad

Ejecutar:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Documentar total final.

---

## 16. Documentación de salida

Crear exactamente:

```text
/docs/02-arquitectura/M06-etapa-3-cierre.md
/docs/04-calidad/M06-pruebas-persistencia-seguridad-outbox-in-app.md
```

### Cierre

Debe incluir:

- rama y base;
- migración;
- riesgo crítico y cierre;
- decisión sobre `create_notification`;
- decisión sobre `NotificationDispatcher`;
- modelo;
- tablas;
- RLS;
- RPC;
- grants;
- eventos M01–M05;
- repositorios;
- inbox;
- preferencias;
- instalaciones;
- Realtime;
- legacy;
- archivos;
- pruebas;
- build/lint;
- deuda;
- staging;
- checklist;
- parada.

### Pruebas

Debe incluir:

- matriz de seguridad;
- persistencia;
- preferencias;
- instalaciones;
- outbox;
- eventos;
- regresión;
- comandos;
- total;
- resultados;
- limitaciones;
- staging.

---

## 17. Criterios de aceptación

- [ ] Commit base correcto.
- [ ] Rama correcta.
- [ ] Solo migración `026` nueva, salvo defecto real documentado.
- [ ] `001`–`025` sin ediciones.
- [ ] INSERT inseguro cerrado.
- [ ] `create_notification` cross-user cerrado.
- [ ] Android no elige recipient.
- [ ] Android no escribe outbox/deliveries.
- [ ] Inbox server-side.
- [ ] Idempotencia y dedup.
- [ ] Preferencias persistidas.
- [ ] Instalaciones multi-device persistidas.
- [ ] Logout por instalación en contrato/repositorio.
- [ ] INTERNAL protegido.
- [ ] Deep link sin autoridad implícita.
- [ ] Eventos M01–M05 server-derived donde exista fuente segura.
- [ ] Legacy legible.
- [ ] Sin duplicación legacy/nueva.
- [ ] Realtime validado o pendiente honestamente.
- [ ] Tests verdes.
- [ ] Build/lint verdes.
- [ ] Auth/username intactos.
- [ ] Staging `014`–`026` pendiente o validado con evidencia.
- [ ] Sin Etapa 4.
- [ ] Sin M07.
- [ ] Sin merge a main.

---

## 18. Parada

No iniciar Etapa 4.

No ampliar push FCM.

No iniciar M07.

No corregir username/auth en esta rama.

No hacer merge a `main`.

No aplicar producción.

Detenerse al crear:

```text
/docs/02-arquitectura/M06-etapa-3-cierre.md
/docs/04-calidad/M06-pruebas-persistencia-seguridad-outbox-in-app.md
```

No hacer commit hasta revisión.
