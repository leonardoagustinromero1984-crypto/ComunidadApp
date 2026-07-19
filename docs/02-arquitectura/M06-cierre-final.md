# M06 — Cierre final del módulo Notificaciones

**Fecha:** 2026-07-17  
**Producto:** LeoVer  
**Etapas cerradas:** 1–5  
**Base de Etapa 5:** `5e695d77030d81ebeb47f5e31405164e1245b47b`

## 1. Arquitectura M06

M06 adopta una arquitectura server-derived: los módulos de dominio emiten eventos, el backend deriva recipients, materializa una única fila de inbox por destinatario y crea deliveries independientes por canal/instalación. Android consume bandeja, preferencias y navegación, pero no crea avisos cross-user, no escribe outbox/deliveries y no envía FCM.

Componentes persistentes:

- `notifications`: inbox compatible por usuario;
- `notification_events`: evento canónico;
- `notification_preferences`: preferencias por categoría;
- `notification_device_installations`: instalaciones multi-device;
- `notification_deliveries`: estado por canal/instalación;
- `notification_outbox`: desacople transaccional;
- `notification_dead_letters`: fallos agotados o auditados;
- `notification_access_audit`: auditoría de acceso.

## 2. Inbox

`notifications` continúa como fuente de verdad in-app. Conserva campos legacy, sincroniza `is_read` con `state/read_at`, excluye expiradas del unread y soporta mark read/all, archive y delete lógico sobre filas propias. El push no marca la fila como `READ`.

Múltiples instalaciones generan deliveries separados, no filas duplicadas de inbox.

## 3. Eventos y recipients

`notification_events` usa deduplication e idempotency keys estables. Los recipients son derivados server-side en triggers/flujos M03–M05. Android no pasa recipients a un canal privilegiado.

`create_notification` queda como compatibilidad estricta:

- solo destinatario propio;
- solo tipo `SYSTEM`;
- cross-user denegado;
- payload validado.

El INSERT directo cliente en `notifications` permanece denegado.

## 4. Preferencias y quiet hours

Preferencias por categoría:

- push configurable;
- in-app obligatorio para categorías críticas/administrativas;
- marketing OFF por defecto;
- email deshabilitado y mostrado solo como **Próximamente**;
- quiet hours con días y timezone IANA;
- críticas según excepción explícita de política.

Denegar push o el permiso Android no bloquea la bandeja in-app.

## 5. Instalaciones

- `installationId` estable por instalación;
- registro luego de login válido;
- rotación en `onNewToken`;
- revoke solo de la instalación actual al logout;
- cambio de usuario desvincula/reasocia;
- token inválido revoca la instalación;
- raw token no forma parte de modelos/respuestas públicas;
- otros dispositivos permanecen activos;
- se evita doble registro y doble push legacy/nuevo.

`device_tokens` se conserva temporalmente como compatibilidad, pero la ruta gobernada usa `notification_device_installations`.

## 6. Deliveries, outbox y dead-letter

Estados soportados:

`PENDING`, `PROCESSING`, `DELIVERED`, `FAILED_RETRYABLE`, `FAILED_PERMANENT`, `SKIPPED_PREFERENCE`, `SKIPPED_PERMISSION`, `SKIPPED_EXPIRED`, `SKIPPED_QUIET_HOURS`, `CANCELLED`, `DEAD_LETTER`.

El claim es atómico y server-only. Errores transitorios reintentan; token inválido falla permanentemente y revoca; máximo agotado va a dead-letter. La migración 028 endurece grants internos, corrige la idempotencia del enqueue y registra fallos sanitizados que antes podían quedar silenciosos.

## 7. Push y Edge Function

Se reutiliza Firebase Messaging; no se agregó proveedor.

La Edge Function:

- requiere autorización server-side;
- reclama deliveries M06;
- no acepta userId/token/title/body arbitrarios desde Android;
- usa credenciales FCM solo server-side;
- valida el delivery planificado;
- actualiza estado mediante RPC interna;
- revoca tokens inválidos;
- sanitiza logs y respuestas;
- no expone token ni provider message ID al cliente;
- no simula entrega ante fallo FCM.

Payload mínimo: `notification_id`, categoría, prioridad, sensibilidad, tipo de deep link, resourceId, organizationId cuando corresponde y delivery ID. No transporta signed URLs, secretos, SQL, stack traces, credenciales, body INTERNAL ni PII completa. Sensibles usan copy genérico.

## 8. Canales Android

- `leover_security`
- `leover_organizations`
- `leover_moderation_support`
- `leover_pets_adoptions`
- `leover_social_messages`
- `leover_system`
- `leover_default` como compatibilidad

Canal desconocido → `leover_system`. No se recrean/borran canales ya configurados por el usuario.

## 9. Deep links

Allowlist cerrada para inbox, perfil, organizaciones, moderación, verificación, soporte, mascotas, adopciones, casos, archivos, chat y `SAFE_HOME`.

La apertura revalida:

- sesión;
- existencia/recurso cuando existe evidencia;
- permisos M02/M04;
- membresía y permisos M03 dentro de la organización;
- sensibilidad.

AccountType y `active_modules` no otorgan acceso. El payload no prueba membresía. Staff sin permiso y organizaciones incorrectas caen a `SAFE_HOME` o bandeja. Pending navigation evita doble navegación y se limpia en logout/cambio de cuenta.

## 10. Permiso Android

`POST_NOTIFICATIONS` se solicita en contexto desde preferencias, con explicación previa. Se manejan denegación, denegación permanente y acceso a ajustes. No se pide automáticamente al iniciar ni bloquea la aplicación.

## 11. Wiring M03–M05

- **M03:** invitaciones, aceptación/rechazo/expiración, rol, remoción y ownership.
- **M04:** medidas, apelaciones, verificación y soporte; INTERNAL solo para staff autorizado y nunca para requester.
- **M05:** estados de upload y documentos de verificación para staff.

Recipients, organización, deduplicación e idempotencia son server-side. Fallos de notificación no abortan la mutación de dominio, pero quedan auditados.

## 12. Compatibilidad legacy

- inbox y campos legacy conservados;
- `leover_default` conservado;
- `device_tokens` retenido temporalmente;
- `NotificationDispatcher` conservado con deuda explícita.

Call sites legacy restantes: chat, amistad, adopción, foster y booking. Con Supabase, el alta cliente se deniega y no se simula éxito. No hay FCM desde ViewModels.

## 13. Realtime

**REALTIME PENDIENTE**

Sin evidencia autorizada de publicación/RLS/lifecycle. Se mantiene refresh/polling. No se afirma Realtime y no se exponen events, outbox o deliveries.

## 14. Calidad local

- `assembleDebug`: **SUCCESS**
- `testDebugUnitTest`: **489 tests, 0 failures, 0 errors, 0 skipped**
- `lintDebug`: **SUCCESS**

## 15. Staging

**PENDIENTE DE VALIDACIÓN REMOTA**

Sin acceso autorizado. Pendiente validar, con backup y evidencia, la secuencia `014`–`028`. No se aplicó producción.

## 16. Deuda aceptada

- Realtime pendiente.
- Staging pendiente y release bloqueado.
- Validación SQL remota de 028 pendiente.
- `SKIPPED_PREFERENCE` no siempre queda materializado como delivery de auditoría.
- Call sites legacy de `NotificationDispatcher` requieren eventos backend seguros futuros; no forman parte de M06 cerrado.

## 17. Condiciones de release

Antes de release:

1. autorizar un staging no productivo;
2. crear backup/punto de recuperación;
3. verificar historial remoto sin reejecutar migraciones;
4. aplicar únicamente pendientes en orden `014`–`028`;
5. ejecutar matriz de seguridad, inbox, preferencias, instalaciones, push/deep links y wiring;
6. validar secrets y logs de la Edge Function;
7. mantener Realtime deshabilitado salvo evidencia completa;
8. bloquear release ante cualquier fallo.

## 18. Fuera de alcance

M07 no fue iniciado. Email no fue implementado. No se agregaron GPS, mapas, pagos ni proveedor push. `AuthRepository`, `domain/auth` y `UsernameValidators` permanecen intactos.
