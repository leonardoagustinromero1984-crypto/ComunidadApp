# M06 — Cierre Etapa 5: validación, staging, calidad y cierre final

**Fecha:** 2026-07-17  
**Rama:** `m06/etapa-5-validacion-cierre`  
**Commit base:** `5e695d77030d81ebeb47f5e31405164e1245b47b`  
**Producto:** LeoVer  
**Estado:** implementación local sin commit, pendiente de revisión.

---

## 1. Estado Git

- Base confirmada: `5e695d77030d81ebeb47f5e31405164e1245b47b`.
- Rama creada desde esa base: `m06/etapa-5-validacion-cierre`.
- Antes de iniciar había un único archivo no trackeado: la especificación de Etapa 5.
- Migraciones `001`–`027` sin modificaciones.
- Única migración nueva: `028_m06_stage5_security_hardening.sql`.
- Sin merge a `main`, push, staging ni producción.

## 2. Defectos encontrados

### Bloqueantes

1. **EXECUTE público residual en funciones `SECURITY DEFINER`:** 026 revocaba `m06_enqueue_domain_event` y `m06_materialize_in_app_notification` solo de `authenticated`, no de `PUBLIC`/`anon`. Esto podía permitir materialización cross-user si persistía el grant público por defecto.
2. **Idempotencia inválida en enqueue:** `ON CONFLICT` intentaba actualizar `notification_events.updated_at`, columna inexistente. Un reintento podía fallar y el error era ocultado por `m06_emit_domain_notification`.
3. **Fallo silencioso de wiring:** `m06_emit_domain_notification` devolvía `null` sin registrar evidencia al capturar cualquier error.
4. **Reautorización Android incompleta:** `ComunidappNavGraph` copiaba el `organizationId` del payload al contexto autorizado y fijaba `resourceExists=true`, por lo que el payload podía aparentar una membresía o recurso ya probado.

### No bloqueantes / deuda

- Preferencia push desactivada evita planificar el push, pero no persiste un delivery `SKIPPED_PREFERENCE`.
- Realtime no está implementado ni validado en entorno autorizado.
- Siete call sites legacy de `NotificationDispatcher` permanecen inventariados; con Supabase el alta cliente se deniega y no se simula éxito.
- La validación remota completa de schemas/triggers y las migraciones `014`–`028` queda bloqueada sin staging autorizado.

## 3. Correcciones

- Migración 028 revoca `PUBLIC`, `anon` y `authenticated` en enqueue/materialize y RPCs internos de outbox; concede ejecución a `service_role`.
- Enqueue idempotente usa `ON CONFLICT DO NOTHING` y recupera el evento por `idempotency_key`.
- Emit registra un error sanitizado en `notification_dead_letters` antes de devolver `null`, sin abortar la mutación de dominio.
- Se reafirman grants server-only del procesamiento push.
- `NotificationDeepLinkSessionResolver` nunca confía en la organización declarada por la notificación.
- NavGraph obtiene permisos M02 reales, comprueba membresía M03 y arma un contexto fail-closed.
- Rutas sin prueba local de recurso vuelven a fallback seguro.
- Se agregaron nueve pruebas de regresión.

## 4. Migración 028

Creada:

```text
supabase/migrations/028_m06_stage5_security_hardening.sql
```

Alcance mínimo: grants de funciones internas, idempotencia de eventos, auditoría sanitizada de fallos y reafirmación del push server-only. No edita `026` ni `027`, no agrega funcionalidad y no reabre `create_notification`.

## 5. Edge Function

La auditoría confirmó:

- autorización server-side obligatoria;
- claim exclusivo mediante `m06_claim_push_deliveries`;
- ignorado de `userId`, token, title y body arbitrarios;
- credenciales FCM solo en secrets server-side;
- actualización por `m06_mark_delivery_result`;
- token inválido → permanente + revoke;
- logs y respuestas sanitizados;
- copy genérico para contenido sensible;
- push entregado no marca `READ`;
- sin doble envío desde `device_tokens`.

No fue necesario modificar la Edge Function en Etapa 5.

## 6. Archivos modificados / creados

- `app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt`
- `app/src/main/java/com/comunidapp/app/notifications/NotificationDeepLinkSessionResolver.kt`
- `app/src/test/java/com/comunidapp/app/notifications/M06Stage5SecurityHardeningTest.kt`
- `supabase/migrations/028_m06_stage5_security_hardening.sql`
- `docs/03-modulos/M06-Etapa-5-Validacion-Staging-Calidad-y-Cierre-Final.md`
- `docs/02-arquitectura/M06-etapa-5-cierre.md`
- `docs/02-arquitectura/M06-cierre-final.md`
- `docs/04-calidad/M06-reporte-validacion-staging.md`

## 7. Comandos y calidad

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Resultado final:

- `assembleDebug`: **SUCCESS**
- `testDebugUnitTest`: **489 tests, 0 failures, 0 errors, 0 skipped**
- `lintDebug`: **SUCCESS**

La baseline de 480 pruebas se conserva y se amplía en 9.

## 8. Seguridad validada localmente

- INSERT cliente en `notifications`: denegado.
- `create_notification`: self-only y solo `SYSTEM`; cross-user denegado.
- Android no escribe events, outbox ni deliveries.
- Android no elige recipients ni envía FCM desde ViewModels.
- Actor de RPCs cliente derivado de `auth.uid()`.
- Recipients M03–M05 derivados server-side.
- AccountType y `active_modules` no conceden autoridad.
- Roles M03 limitados a membresía/organización probada.
- Staff sin permiso no abre rutas staff.
- INTERNAL de soporte no se envía al requester.
- Tokens, signed URLs y PII completa se rechazan en payload.
- Token inválido revoca instalación.
- Varias instalaciones comparten un inbox por usuario, con un delivery por instalación.
- Logout revoca solo la instalación actual; otros dispositivos permanecen activos.
- No hay doble push legacy/nuevo en la ruta gobernada.
- Deep link denegado vuelve a `SAFE_HOME` o bandeja.
- Logout/cambio de cuenta limpia navegación pendiente.
- Marketing sigue OFF por defecto.
- Email sigue visible únicamente como **Próximamente**.
- Denegar `POST_NOTIFICATIONS` no bloquea la bandeja in-app.
- Auth/username fuera de alcance e intactos.

## 9. Regresión

- M01/auth/username: sin cambios.
- M02: permisos globales consultados para rutas staff.
- M03: membresía y permisos por organización revalidados.
- M04: wiring y rutas staff conservados.
- M05: wiring de uploads/documentos conservado.
- Compatibilidad de inbox legacy y `leover_default`: conservada.
- Mocks `useSupabase=false` y repositorios `useSupabase=true`: suite local aprobada.
- Sin GPS, mapas, pagos, email, nuevo proveedor push ni M07.

## 10. Realtime

**REALTIME PENDIENTE**

No existe evidencia de entorno autorizado para validar publicación, RLS, filas propias, lifecycle de una suscripción por sesión o ausencia de listeners duplicados. La app mantiene `NotificationInboxRefreshCoordinator` y refresh/polling; events, outbox y deliveries no se exponen al cliente.

## 11. Staging

**PENDIENTE DE VALIDACIÓN REMOTA**

No se encontró proyecto Supabase linkeado ni credenciales/entorno de staging autorizado. No se consultó historial remoto, no se generó backup y no se aplicaron migraciones. La secuencia pendiente es:

```text
014 → 015 → 016 → 017 → 018 → 019 → 020 → 021 → 022 → 023 → 024 → 025 → 026 → 027 → 028
```

## 12. Riesgos

- Release bloqueado hasta validar staging con backup y evidencia.
- Realtime no forma parte del cierre funcional.
- Los eventos legacy inventariados en `NotificationDispatcher` dependen de futuros flujos backend seguros; no se simula éxito.
- 028 requiere validación SQL remota antes de release.

## 13. Checklist

- [x] Commit base y rama correctos.
- [x] `001`–`027` sin ediciones.
- [x] 026 y 027 auditadas.
- [x] Defectos bloqueantes corregidos en 028.
- [x] Cross-user e INSERT cliente cerrados.
- [x] Android sin escritura de outbox/deliveries ni elección de recipient.
- [x] Payload y Edge Function seguros.
- [x] Inbox legacy compatible.
- [x] Preferencias, instalaciones y deliveries auditados.
- [x] Push no marca READ.
- [x] Deep links reautorizados fail-closed.
- [x] INTERNAL protegido.
- [x] Wiring M03–M05 auditado.
- [x] Realtime declarado pendiente.
- [x] 489 tests aprobados.
- [x] Build y lint aprobados.
- [x] Staging declarado pendiente sin simulación.
- [x] Auth/username intactos.
- [x] Sin M07, email, merge o producción.

## 14. Parada

Etapa 5 queda implementada y documentada localmente, **sin commit**. M07 no fue iniciado.
