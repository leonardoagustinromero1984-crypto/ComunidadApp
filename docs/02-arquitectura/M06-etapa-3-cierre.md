# M06 — Cierre Etapa 3: Persistencia, seguridad, outbox y entrega in-app

**Fecha:** 2026-07-16  
**Rama:** `m06/etapa-3-persistencia-seguridad-outbox-in-app`  
**Commit base:** `c38fb3aaecf34badee1478e7388d851f60f4165c`  
**Producto:** LeoVer  
**Estado:** implementación local sin commit, pendiente de revisión.

---

## 1. Resumen

Etapa 3 cierra la superficie crítica por la que Android podía crear notificaciones para terceros y establece una base server-side para bandeja in-app, preferencias, instalaciones, outbox, deliveries y dead-letter.

La tabla `public.notifications` se conserva como bandeja por usuario y se evoluciona de forma compatible con filas legacy. `is_read` sigue disponible y queda sincronizado con `state/read_at` durante la transición.

No se amplió FCM, no se crearon Edge Functions, jobs, cron ni WorkManager, no se inició Etapa 4, no se inició M07 y no se aplicó staging ni producción.

---

## 2. Migración

Creada:

```text
supabase/migrations/026_m06_notifications_security_outbox_foundation.sql
```

No se editaron migraciones `001–025`.

La migración:

- elimina la policy insegura `notifications_insert WITH CHECK (true)`;
- crea una policy `notifications_no_insert_client` con `WITH CHECK (false)`;
- bloquea DELETE físico desde Android;
- mantiene SELECT solo para `auth.uid() = user_id`;
- permite updates solo sobre filas propias;
- agrega campos M06 a `public.notifications`;
- conserva lectura legacy y dedup legacy por `legacy:{id}`;
- agrega trigger de sincronización `is_read` / `read_at` / `state`;
- conserva `device_tokens` legacy y documenta su coexistencia.

---

## 3. Decisión sobre `create_notification`

`create_notification` fue reemplazada por una variante `SECURITY DEFINER` con `search_path = public`, actor derivado desde `auth.uid()` y modo legacy estricto:

- cross-user queda denegado con `M06_CREATE_NOTIFICATION_CROSS_USER_DENIED`;
- solo admite self-only para `SYSTEM`;
- no acepta tipos cliente que calculen destinatarios como chat, amistad, adopción, organización, soporte o moderación;
- valida payload para rechazar URLs arbitrarias, signed URLs, tokens, secretos, SQL, stack traces y PII completa.

Resultado: Android ya no puede elegir `recipient`.

---

## 4. Decisión sobre `NotificationDispatcher`

`NotificationDispatcher` no fue eliminado. Queda como compatibilidad temporal, pero ya no puede crear notificaciones cross-user cuando `useSupabase=true`:

- `PlatformSupabaseDataSource.createNotification` devuelve `SecurityException("NOTIFICATION_CLIENT_INSERT_DENIED_M06_STAGE_3")`;
- el dispatcher registra la denegación y no simula éxito;
- flujos legacy de chat, amistad, adopción, foster, booking y comunidad pueden quedar temporalmente sin aviso hasta que se migren a eventos server-derived.

No se amplió FCM.

---

## 5. Tablas, RLS y RPC

Tablas nuevas:

- `notification_events`
- `notification_preferences`
- `notification_device_installations`
- `notification_deliveries`
- `notification_outbox`
- `notification_dead_letters`
- `notification_access_audit`

RPC / funciones equivalentes:

- `m06_require_active_actor`
- `m06_validate_payload`
- `m06_can_read_notification`
- `m06_enqueue_domain_event`
- `m06_materialize_in_app_notification`
- `m06_claim_outbox`
- `m06_mark_outbox_processed`
- `m06_mark_outbox_failed`
- `m06_get_inbox`
- `m06_get_unread_count`
- `m06_mark_notification_read`
- `m06_mark_all_notifications_read`
- `m06_archive_notification`
- `m06_delete_notification_logical`
- `m06_get_preferences`
- `m06_update_preference`
- `m06_register_installation`
- `m06_rotate_installation_token`
- `m06_revoke_current_installation`

RLS deny-by-default:

- `notifications`: SELECT solo destinatario; sin INSERT cliente; sin DELETE físico cliente.
- preferencias e instalaciones: solo propias.
- eventos, deliveries, outbox, dead letters y audit: sin escritura cliente.
- AccountType y `active_modules` no participan en autorización.
- staff queda condicionado a permisos existentes, no a rol nominal.

---

## 6. Android

Repositorios reales creados:

- `SupabaseNotificationInboxRepository`
- `SupabaseNotificationPreferenceRepository`
- `SupabaseNotificationInstallationRepository`

Repositorios cliente denegados:

- `ClientDeniedNotificationDeliveryRepository`
- `ClientDeniedNotificationOutboxRepository`

Integración:

- `DataProvider` usa Supabase real para inbox/preferencias/instalaciones cuando `useSupabase=true`.
- `PlatformSupabaseDataSource` lee la bandeja mediante `m06_get_inbox` y marca lectura mediante RPC M06.
- `NotificationsScreen` mantiene el diseño existente y suma acciones discretas de archivar y eliminar lógico.
- `PushTokenRegistrar` registra instalación M06 con fingerprint de token y revoca solo la instalación actual en logout.
- `device_tokens` legacy no se borra en logout.

---

## 7. Eventos M01–M05

Implementado en esta etapa:

- Foundation server-side para encolar eventos (`m06_enqueue_domain_event`) y materializar inbox (`m06_materialize_in_app_notification`) con idempotencia, dedup y recipient server-side.
- Clasificación allowlisted por `origin_module` M01–M05.

Pendiente por no completar wiring transaccional seguro sin ampliar alcance:

- M03: invitaciones, aceptación/rechazo/expiración, cambio de rol, remoción y transferencia de ownership deben integrarse dentro de RPCs M03 existentes.
- M04: moderación, apelaciones, verificación y soporte deben integrarse dentro de RPCs M04 existentes evitando INTERNAL al requester.
- M05: uploads y documentos de verificación deben integrarse dentro de RPCs M05 existentes.
- M01/M02: estado de cuenta, rol/permiso crítico y eliminación quedan pendientes hasta poder derivarlos sin modificar autenticación.

No se inventaron eventos sin fuente transaccional segura.

---

## 8. Realtime

Realtime sobre `notifications` queda **pendiente**.

La bandeja opera por polling/RPC existente. No se afirma Realtime operativo porque no fue validado localmente ni en staging con RLS activa, suscripción por usuario, cierre en logout/cambio de cuenta y ausencia de listeners duplicados.

---

## 9. Calidad

Comandos ejecutados:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Resultados:

- `assembleDebug`: SUCCESS
- `testDebugUnitTest`: **440 tests, 0 failures, 0 errors**
- `lintDebug`: SUCCESS

Se conservaron las 431 pruebas existentes y se agregaron 9 pruebas M06 Etapa 3.

---

## 10. Staging

Estado remoto:

```text
PENDIENTE DE VALIDACIÓN REMOTA
```

Migraciones `014–026` siguen pendientes de validación/aplicación en staging con evidencia. No se aplicó producción.

---

## 11. Auth y username

Confirmado sin cambios:

- `AuthRepository`
- `domain/auth`
- `UsernameValidators`
- pruebas de auth/username existentes

---

## 12. Parada

Etapa 3 queda implementada localmente y documentada, sin commit.

No iniciar Etapa 4.  
No ampliar FCM.  
No iniciar M07.  
No hacer merge a `main`.  
No aplicar producción.
