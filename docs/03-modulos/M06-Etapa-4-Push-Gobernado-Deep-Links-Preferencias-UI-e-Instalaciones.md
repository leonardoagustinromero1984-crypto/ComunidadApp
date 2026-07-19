# LEOVER — M06 Etapa 4: Push Gobernado, Deep Links Operativos, Preferencias UI e Instalaciones

**Módulo:** M06 — Notificaciones  
**Etapa:** 4 — Push gobernado, deep links operativos, preferencias UI e instalaciones reales  
**Estado de entrada:** Etapa 3 aprobada y consolidada  
**Commit base:** `824a091df9bda80c40492c64486d6d5ecd9d0c06`  
**Rama base:** `m06/etapa-3-persistencia-seguridad-outbox-in-app`  
**Calidad de entrada:** 440 tests, 0 failures, 0 errors; `assembleDebug` y `lintDebug` en SUCCESS  
**Staging heredado:** migraciones `014`–`026` pendientes de validación remota  
**Objetivo:** conectar el push existente de forma gobernada con el modelo M06, habilitar deep links seguros, preferencias UI, instalaciones por dispositivo y actualización de la bandeja, sin ampliar a email ni iniciar M07.

---

## 1. Documentos obligatorios

Leer en este orden:

1. `/docs/02-arquitectura/M06-auditoria-inicial.md`
2. `/docs/02-arquitectura/M06-etapa-2-cierre.md`
3. `/docs/02-arquitectura/M06-etapa-3-cierre.md`
4. `/docs/04-calidad/M06-pruebas-persistencia-seguridad-outbox-in-app.md`
5. `/docs/03-modulos/M06-Notificaciones.md`
6. `/docs/03-modulos/M06-Etapa-3-Persistencia-Seguridad-Outbox-y-Entrega-In-App.md`
7. ADR-0001 a ADR-0005
8. Este documento.

---

## 2. Protección Git

1. Confirmar commit base:

```text
824a091df9bda80c40492c64486d6d5ecd9d0c06
```

2. Confirmar working tree limpio.
3. Crear la rama:

```text
m06/etapa-4-push-deep-links-preferencias-instalaciones
```

4. No mezclar GPS, mapas ni pagos.
5. No hacer merge a `main`.
6. No iniciar M07.
7. No modificar username/auth.
8. No aplicar producción.
9. No afirmar staging sin evidencia.

---

## 3. Alcance autorizado

### 3.1 Push gobernado

Reutilizar la integración FCM existente. No agregar un segundo proveedor ni un segundo SDK.

Implementar:

- consumo seguro de deliveries M06;
- payload mínimo y tipado;
- categoría, sensibilidad y prioridad;
- notification channel Android por categoría funcional;
- lock-screen copy genérico para sensibles;
- token refresh por instalación;
- revocación de token inválido;
- múltiples dispositivos;
- deduplicación por `notification_id` y delivery;
- idempotencia;
- errores sanitizados;
- no simular entrega si FCM falla;
- no marcar READ por recibir push.

No incluir en push:

- signed URLs;
- token FCM;
- secretos;
- SQL;
- stack trace;
- body INTERNAL;
- PII completa;
- contenido sensible detallado.

### 3.2 Edge Function `push`

Auditar y endurecer la función existente `supabase/functions/push`.

Permitido:

- adaptar la función existente al modelo M06;
- validar deliveries pendientes;
- usar credenciales FCM solo server-side;
- actualizar estado de delivery mediante una función interna segura;
- revocar instalaciones inválidas;
- sanitizar respuestas y logs;
- mantener idempotencia.

No permitido:

- aceptar `user_id`, token, title o body arbitrarios desde Android;
- exponer credenciales;
- permitir invocación cliente sin autorización;
- enviar desde ViewModels;
- crear otro proveedor push.

Si requiere una migración correctiva para grants, webhook o RPC, crear únicamente:

```text
supabase/migrations/027_m06_push_delivery_and_installation_hardening.sql
```

No editar `026` ni migraciones anteriores.

### 3.3 Deep links operativos

Implementar navegación tipada para:

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

- allowlist cerrada;
- no URI arbitraria;
- validar sesión;
- validar recurso;
- validar permiso;
- validar organización;
- staff routes con permiso M02/M04;
- organización limitada por M03;
- recurso inexistente o permiso denegado → `SAFE_HOME` o inbox;
- notificación nunca concede autoridad;
- payload push lleva IDs mínimos;
- navegación pendiente se limpia en logout/cambio de cuenta;
- evitar navegación duplicada por reentrega.

### 3.4 Preferencias UI

Crear una pantalla o sección coherente con la UI existente para:

- preferencias por categoría;
- push on/off;
- email visible como “próximamente” o deshabilitado si no está implementado;
- quiet hours;
- timezone;
- marketing OFF por defecto;
- categorías obligatorias in-app no desactivables;
- explicación de eventos críticos;
- estados de guardado, error y reintento;
- accesibilidad y textos claros.

No afirmar que email funciona.

### 3.5 Permiso Android

Gestionar `POST_NOTIFICATIONS`:

- pedirlo en contexto, no automáticamente al abrir la app;
- explicar el beneficio antes del prompt del sistema;
- manejar denegación;
- manejar “no volver a preguntar”;
- acceso a ajustes del sistema;
- no bloquear el uso de la app;
- in-app continúa funcionando sin push.

### 3.6 Instalaciones reales

Integrar `notification_device_installations` con el servicio FCM existente:

- `installationId` estable por instalación;
- registrar instalación después de sesión válida;
- rotar token en `onNewToken`;
- revocar solo instalación actual al logout;
- cambio de usuario desvincula instalación anterior;
- token inválido revoca instalación;
- otros dispositivos permanecen activos;
- nunca mostrar raw token;
- `device_tokens` legacy continúa solo como compatibilidad temporal, sin doble envío.

### 3.7 Bandeja y actualización

Completar:

- actualización después de push;
- refresh manual;
- unread consistente;
- mark read/all;
- archive;
- delete lógico;
- expiración;
- agrupación visual opcional sin perder trazabilidad;
- fallback legacy;
- evitar duplicados.

Realtime puede implementarse si se valida correctamente. Si no:

- mantener polling/refresh;
- documentarlo como pendiente;
- no afirmar operación Realtime.

---

## 4. Wiring server-derived M03–M05

Etapa 3 dejó foundation pero sin wiring transaccional. En Etapa 4 integrar eventos seguros dentro de RPCs o flujos backend existentes, sin ViewModels calculando destinatarios.

### M03

Prioridad:

- invitación creada;
- invitación aceptada/rechazada/expirada;
- cambio de rol;
- remoción;
- transferencia de ownership, si el flujo existe.

### M04

Prioridad:

- medida aplicada;
- apelación presentada/resuelta;
- verificación `MORE_INFO`, `APPROVED`, `REJECTED`, `REVOKED`;
- ticket creado;
- respuesta visible;
- actualización INTERNAL solo para staff autorizado.

### M05

Prioridad:

- upload completado;
- upload fallido/cancelado cuando exista transición persistida;
- documento de verificación disponible para staff.

Reglas:

- misma transacción cuando sea posible;
- idempotency key estable;
- deduplication key estable;
- recipient calculado server-side;
- respetar permisos y organización;
- INTERNAL nunca al requester;
- no inventar eventos sin fuente segura;
- documentar implementado/parcial/pendiente.

Si el wiring requiere cambios SQL, incluirlos únicamente en `027`.

---

## 5. NotificationDispatcher y flujos legacy

Objetivo: retirar la dependencia de emisiones cross-user desde Android.

- inventariar todos los call sites;
- reemplazar por eventos server-derived donde el backend seguro exista;
- donde todavía no exista, devolver estado explícito “aviso pendiente/no disponible” sin simular éxito;
- no volver a habilitar `create_notification` cross-user;
- no enviar FCM directamente desde ViewModel;
- mantener compatibilidad de lectura;
- documentar deuda restante.

No eliminar código necesario para mocks o compatibilidad sin prueba de regresión.

---

## 6. Canales Android

Definir canales estables, sin crear uno por notificación:

```text
leover_security
leover_organizations
leover_moderation_support
leover_pets_adoptions
leover_social_messages
leover_system
```

Reglas:

- nombres visibles localizables;
- prioridad coherente;
- sonido/vibración no abusivos;
- `URGENT` restringido;
- sensibles con texto genérico;
- canal desconocido → `leover_system`;
- no borrar canales ya creados por usuarios;
- migrar desde `leover_default` sin romper notificaciones existentes.

---

## 7. Estado de entrega

Conectar el procesamiento a estados M06:

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

- claim atómico;
- un delivery por instalación/canal;
- reintento con backoff;
- token inválido → permanente + revoke;
- preferencia desactivada → skip;
- quiet hours → defer/skip según contrato;
- expirado → skip;
- máximo agotado → dead-letter;
- provider message id no se muestra al usuario;
- push entregado no marca notificación READ.

---

## 8. Seguridad

Confirmar:

- service account/credenciales solo server-side;
- Android no conoce server key;
- Edge Function no acepta recipient arbitrario;
- deliveries no son escribibles por cliente;
- raw tokens no se listan;
- logs sin tokens ni body sensible;
- payload allowlisted;
- deep links reautorizados;
- AccountType/modules sin autoridad;
- M03 scoped a organización;
- M04 staff por permiso;
- INTERNAL staff-only;
- rate limiting razonable;
- dedup/idempotencia;
- logout limpia navegación pendiente, listeners y estado temporal;
- cambio de cuenta no reutiliza datos de la anterior.

---

## 9. Android y clases esperadas

Crear o adaptar componentes equivalentes a:

```text
NotificationPushPayloadParser
NotificationChannelRegistry
NotificationDeepLinkRouter
NotificationPendingNavigationStore
NotificationPermissionCoordinator
NotificationPreferencesViewModel
NotificationPreferencesScreen
NotificationInstallationCoordinator
NotificationInboxRefreshCoordinator
NotificationUiErrorMapper
```

Adaptar:

```text
LeoverFirebaseMessagingService
PushTokenRegistrar
MainActivity / navegación
NotificationsViewModel
NotificationsScreen
DataProvider
```

Reglas:

- no duplicar repositorios;
- usar contratos M06;
- estados UI claros;
- doble tap idempotente;
- limpiar datos sensibles al logout;
- `useSupabase=false` con mocks funcionales;
- `useSupabase=true` con repositorios M06 reales.

---

## 10. Pruebas obligatorias

Conservar las 440 pruebas existentes.

Agregar pruebas para:

### Push y payload

- payload válido;
- route desconocida → SAFE_HOME;
- signed URL/token/PII rechazados;
- sensible usa copy genérico;
- duplicado no muestra dos notificaciones;
- push no marca READ;
- canal correcto;
- canal desconocido → system;
- token inválido revoca instalación;
- error transitorio reintenta;
- error permanente no reintenta.

### Deep links

- sesión ausente;
- recurso inexistente;
- permiso denegado;
- organización incorrecta;
- staff sin permiso;
- INTERNAL;
- doble tap;
- logout limpia navegación pendiente;
- cambio de cuenta.

### Preferencias

- push desactivado;
- categoría obligatoria in-app;
- marketing OFF;
- quiet hours mismo día y cruce medianoche;
- timezone/DST;
- SECURITY_CRITICAL con excepción explícita;
- guardado/error/retry.

### Instalaciones

- registro después de login;
- rotación de token;
- logout revoca actual;
- otro dispositivo queda activo;
- cambio de usuario;
- no doble registro;
- coexistencia legacy sin doble push.

### Wiring M03–M05

- recipient correcto;
- idempotencia;
- dedup;
- org scope;
- permisos staff;
- INTERNAL no requester;
- evento sin fuente segura no inventado.

### Regresión

- 440 pruebas previas;
- bandeja legacy;
- FCM existente;
- M01–M05;
- auth/username intactos;
- M05 cleanup;
- WIP aislado.

---

## 11. Calidad

Ejecutar:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Documentar el total final.

---

## 12. Documentación de salida

Crear exactamente:

```text
/docs/02-arquitectura/M06-etapa-4-cierre.md
/docs/04-calidad/M06-pruebas-push-deep-links-preferencias-instalaciones.md
```

El cierre debe incluir:

- rama y base;
- migración `027` o ausencia;
- Edge Function;
- deliveries;
- canales Android;
- payload;
- deep links;
- preferencias UI;
- permiso Android;
- instalaciones;
- inbox/Realtime;
- wiring M03–M05;
- NotificationDispatcher;
- archivos;
- pruebas;
- build/lint;
- deuda;
- staging;
- checklist;
- parada.

---

## 13. Criterios de aceptación

- [ ] Commit base correcto.
- [ ] Rama correcta.
- [ ] `001`–`026` sin ediciones.
- [ ] Solo `027` nueva si es necesaria.
- [ ] FCM existente reutilizado.
- [ ] Sin segundo proveedor push.
- [ ] Edge Function endurecida.
- [ ] Android no elige recipient.
- [ ] Payload mínimo y seguro.
- [ ] Canales Android estables.
- [ ] Deep links allowlisted y reautorizados.
- [ ] Preferencias UI funcionales.
- [ ] Quiet hours funcionales.
- [ ] Permiso Android contextual.
- [ ] Instalación actual revocada al logout.
- [ ] Otros dispositivos activos.
- [ ] Token inválido revocado.
- [ ] Inbox sin duplicados.
- [ ] Push no marca READ.
- [ ] Wiring M03–M05 seguro o documentado pendiente.
- [ ] NotificationDispatcher sin emisiones inseguras.
- [ ] Realtime validado o pendiente honestamente.
- [ ] Tests verdes.
- [ ] Build/lint verdes.
- [ ] Auth/username intactos.
- [ ] Staging `014`–`027` pendiente o validado con evidencia.
- [ ] Sin email.
- [ ] Sin M07.
- [ ] Sin merge a main.

---

## 14. Parada

No iniciar M06 Etapa 5.

No implementar email.

No iniciar M07.

No corregir username/auth en esta rama.

No hacer merge a `main`.

No aplicar producción.

Detenerse al crear:

```text
/docs/02-arquitectura/M06-etapa-4-cierre.md
/docs/04-calidad/M06-pruebas-push-deep-links-preferencias-instalaciones.md
```

No hacer commit hasta revisión.
