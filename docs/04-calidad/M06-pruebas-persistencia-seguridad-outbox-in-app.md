# M06 — Pruebas Etapa 3: persistencia, seguridad, outbox e in-app

**Fecha:** 2026-07-16  
**Rama:** `m06/etapa-3-persistencia-seguridad-outbox-in-app`  
**Commit base:** `c38fb3aaecf34badee1478e7388d851f60f4165c`  
**Estado:** local aprobado, staging pendiente.

---

## 1. Comandos

Ejecutados:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Resultado:

- `assembleDebug`: SUCCESS
- `testDebugUnitTest`: **440 tests, 0 failures, 0 errors**
- `lintDebug`: SUCCESS

---

## 2. Pruebas agregadas

Archivos:

- `M06Migration026SecurityOutboxTest`
- `M06AndroidNotificationClientBoundaryTest`

Total agregado: 9 tests.

---

## 3. Matriz de seguridad

Cubierto por pruebas estáticas/Kotlin:

- INSERT directo cliente reemplazado por `notifications_no_insert_client WITH CHECK (false)`.
- `create_notification` cross-user denegado.
- Android no puede usar `PlatformSupabaseDataSource.createNotification` para crear avisos cliente.
- Android no puede encolar outbox.
- Android no puede escribir deliveries.
- Payload con URL, signed URL, token/secreto, SQL/stack trace o PII completa rechazado por `m06_validate_payload`.
- Auth/username sin modificaciones.

Cubierto por migración:

- SELECT de `notifications` solo destinatario.
- DELETE físico cliente denegado.
- eventos/outbox/deliveries/dead letters sin escritura cliente.
- funciones `SECURITY DEFINER` con `search_path = public` donde corresponde.
- actor derivado desde `auth.uid()`.

---

## 4. Persistencia e inbox

Cubierto:

- `public.notifications` conservada como bandeja por usuario.
- Campos nuevos M06 agregados sin editar `012`.
- Compatibilidad legacy con `type/title/body/related_id/related_type/read_at`.
- `is_read` sincronizado con `state/read_at`.
- deduplicación legacy por `legacy:{id}`.
- expiradas fuera de unread en `m06_get_inbox`/`m06_get_unread_count`.
- archive/delete lógico mediante RPC.

---

## 5. Preferencias

Cubierto:

- `notification_preferences` por usuario y categoría.
- marketing OFF por defecto.
- in-app obligatoria para categorías administrativas/sensibles.
- quiet hours persistidas como horario local, días y timezone.
- otro usuario no puede modificar preferencias por RLS.

---

## 6. Instalaciones

Cubierto:

- `notification_device_installations` persistida.
- registro de instalación por `installation_id`.
- rotación de token por fingerprint.
- revocación de instalación actual.
- raw token no se devuelve en RPC de instalación.
- `device_tokens` legacy permanece y no se elimina todavía.
- logout Android revoca solo instalación actual y deja otros dispositivos vigentes.

---

## 7. Outbox y dead-letter

Cubierto:

- `notification_events` con `idempotency_key` unique.
- `notification_outbox` con claim atómico por `FOR UPDATE SKIP LOCKED`.
- retry con backoff acotado.
- máximo agotado pasa a `notification_dead_letters`.
- errores sanitizados mediante `m06_sanitize_error_code`.
- Android sin grants para outbox, deliveries ni dead letters.

---

## 8. Eventos M01–M05

Implementado como foundation:

- `m06_enqueue_domain_event` admite solo `origin_module` M01–M05.
- `m06_materialize_in_app_notification` materializa recipients calculados server-side.
- INTERNAL requiere permiso staff conocido.
- evento expirado no genera notificación.

Pendiente de validación/wiring:

- M03 invitaciones, cambios de rol, remoción y ownership dentro de RPCs M03.
- M04 moderación, apelación, verificación y soporte dentro de RPCs M04.
- M05 uploads/documentos dentro de RPCs M05.
- M01/M02 cuenta/roles críticos sin modificar autenticación.

No se inventaron eventos sin fuente backend segura.

---

## 9. Regresión

Conservado:

- 431 pruebas existentes.
- FCM legacy no ampliado.
- `device_tokens` no eliminado.
- pantalla existente de notificaciones sin rediseño masivo.
- `NotificationDispatcher` no eliminado y sin éxito simulado cuando Supabase deniega.
- `AuthRepository`, `domain/auth` y `UsernameValidators` intactos.

---

## 10. Limitaciones

- Realtime sobre `notifications` queda pendiente; no se afirma operativo.
- No se aplicó migración en staging.
- No se validó RLS remota con usuarios reales.
- Flujos legacy que dependían de `NotificationDispatcher` pueden quedar temporalmente sin aviso hasta migrarse a eventos server-side.

---

## 11. Staging

```text
PENDIENTE DE VALIDACIÓN REMOTA
```

Migraciones `014–026` deben validarse en staging con evidencia antes de cualquier release.
