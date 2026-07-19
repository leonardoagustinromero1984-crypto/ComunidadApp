# M06 — Reporte de validación staging

**Fecha:** 2026-07-17  
**Rama:** `m06/etapa-5-validacion-cierre`  
**Commit base:** `5e695d77030d81ebeb47f5e31405164e1245b47b`  
**Entorno objetivo:** staging no productivo  
**Actor técnico:** Auto (Cursor), auditoría local estática  
**Estado general:** **PENDIENTE DE VALIDACIÓN REMOTA**

## 1. Acceso y evidencia

No existe acceso autorizado demostrable a un proyecto Supabase staging:

- no hay `supabase/config.toml` linkeado con project ref real;
- ejemplos/configuración contienen placeholders;
- no se dispuso de credenciales staging ni autorización para uso remoto;
- no se consultó historial remoto;
- no se generó backup/punto de recuperación;
- no se aplicó ninguna migración;
- producción no fue utilizada.

Por lo tanto, todos los casos remotos figuran como **NO EJECUTADO**. No se simulan resultados.

## 2. Historial de migraciones

**Caso:** verificar historial remoto `014`–`028`  
**Entorno:** staging no productivo  
**Fecha:** 2026-07-17  
**Actor técnico:** NO EJECUTADO  
**Resultado:** **NO EJECUTADO**  
**Evidencia:** sin proyecto staging linkeado ni acceso autorizado  
**Observaciones:** revisar historial real antes de aplicar; no reejecutar ni editar migraciones aplicadas.

Secuencia pendiente:

```text
014 → 015 → 016 → 017 → 018 → 019 → 020 → 021 → 022 → 023 → 024 → 025 → 026 → 027 → 028
```

## 3. Backup / recuperación

**Caso:** crear backup o punto de recuperación previo  
**Entorno:** staging no productivo  
**Fecha:** 2026-07-17  
**Actor técnico:** NO EJECUTADO  
**Resultado:** **NO EJECUTADO**  
**Evidencia:** acceso remoto ausente  
**Observaciones:** condición obligatoria antes de aplicar la primera migración pendiente.

## 4. Seguridad de creación

**Caso:** usuario A intenta crear una notificación para B  
**Entorno:** staging no productivo  
**Fecha:** 2026-07-17  
**Actor técnico:** NO EJECUTADO  
**Resultado:** **NO EJECUTADO**  
**Evidencia local:** 026 contiene `M06_CREATE_NOTIFICATION_CROSS_USER_DENIED`; 028 revoca EXECUTE público residual en enqueue/materialize  
**Observaciones:** confirmar remote que `create_notification` sea self-only `SYSTEM`.

**Caso:** INSERT directo cliente en `notifications`  
**Entorno:** staging no productivo  
**Fecha:** 2026-07-17  
**Actor técnico:** NO EJECUTADO  
**Resultado:** **NO EJECUTADO**  
**Evidencia local:** policy `notifications_no_insert_client` y ausencia de grant INSERT  
**Observaciones:** probar con JWT authenticated.

**Caso:** Android intenta escribir events/outbox/deliveries  
**Entorno:** staging no productivo  
**Fecha:** 2026-07-17  
**Actor técnico:** NO EJECUTADO  
**Resultado:** **NO EJECUTADO**  
**Evidencia local:** RLS deny-by-default, tables revocadas y repositorios Android `ClientDenied*`  
**Observaciones:** probar RPC/table writes con JWT de usuario.

## 5. Inbox y multi-device

**Caso:** filas propias, unread, expiración y archive/delete lógico  
**Entorno:** staging no productivo  
**Fecha:** 2026-07-17  
**Actor técnico:** NO EJECUTADO  
**Resultado:** **NO EJECUTADO**  
**Evidencia local:** RPCs y pruebas Kotlin aprobadas  
**Observaciones:** validar RLS con usuarios A/B.

**Caso:** varias instalaciones, un inbox y delivery por instalación  
**Entorno:** staging no productivo  
**Fecha:** 2026-07-17  
**Actor técnico:** NO EJECUTADO  
**Resultado:** **NO EJECUTADO**  
**Evidencia local:** índice único push notification/channel/installation  
**Observaciones:** confirmar otro dispositivo activo luego de logout del actual.

## 6. Preferencias y quiet hours

**Caso:** push por categoría, in-app obligatorio y marketing OFF  
**Entorno:** staging no productivo  
**Fecha:** 2026-07-17  
**Actor técnico:** NO EJECUTADO  
**Resultado:** **NO EJECUTADO**  
**Evidencia local:** repositorio, reglas y pantalla aprobados por suite local  
**Observaciones:** email debe mostrarse solo como Próximamente.

**Caso:** quiet hours, días, timezone y DST  
**Entorno:** staging no productivo  
**Fecha:** 2026-07-17  
**Actor técnico:** NO EJECUTADO  
**Resultado:** **NO EJECUTADO**  
**Evidencia local:** tests de reglas y RPC de preferencias  
**Observaciones:** verificar defer/skip y excepción crítica explícita.

## 7. Push y Edge Function

**Caso:** Edge Function requiere autorización server-side  
**Entorno:** staging no productivo  
**Fecha:** 2026-07-17  
**Actor técnico:** NO EJECUTADO  
**Resultado:** **NO EJECUTADO**  
**Evidencia local:** `authorizeService` y claim `m06_claim_push_deliveries`  
**Observaciones:** verificar rechazo sin service role y secretos configurados.

**Caso:** token inválido → permanente + revoke  
**Entorno:** staging no productivo  
**Fecha:** 2026-07-17  
**Actor técnico:** NO EJECUTADO  
**Resultado:** **NO EJECUTADO**  
**Evidencia local:** `m06_mark_delivery_result` + pruebas  
**Observaciones:** respuesta/log no debe exponer token/provider ID.

**Caso:** payload sensible, signed URL, token y PII  
**Entorno:** staging no productivo  
**Fecha:** 2026-07-17  
**Actor técnico:** NO EJECUTADO  
**Resultado:** **NO EJECUTADO**  
**Evidencia local:** parser/validación y copy genérico  
**Observaciones:** inspeccionar FCM request y logs sanitizados.

**Caso:** push entregado no marca READ y no hay doble push  
**Entorno:** staging no productivo  
**Fecha:** 2026-07-17  
**Actor técnico:** NO EJECUTADO  
**Resultado:** **NO EJECUTADO**  
**Evidencia local:** RPC no actualiza inbox; Edge usa installations, no `device_tokens`  
**Observaciones:** confirmar con dos instalaciones y webhook/cron real.

## 8. Deep links y permiso Android

**Caso:** sesión, recurso, permiso y organización reautorizados  
**Entorno:** staging + dispositivo Android de prueba  
**Fecha:** 2026-07-17  
**Actor técnico:** NO EJECUTADO  
**Resultado:** **NO EJECUTADO**  
**Evidencia local:** resolver fail-closed, permisos M02 y membresía M03  
**Observaciones:** denegado → `SAFE_HOME`/bandeja; staff sin permiso no abre rutas staff.

**Caso:** doble tap, reentrega, logout y cambio de cuenta  
**Entorno:** staging + dispositivo Android de prueba  
**Fecha:** 2026-07-17  
**Actor técnico:** NO EJECUTADO  
**Resultado:** **NO EJECUTADO**  
**Evidencia local:** pending store y pruebas  
**Observaciones:** validar proceso muerto/restore y cuentas A/B.

**Caso:** POST_NOTIFICATIONS denegado/permanente  
**Entorno:** dispositivo Android API 33+  
**Fecha:** 2026-07-17  
**Actor técnico:** NO EJECUTADO  
**Resultado:** **NO EJECUTADO**  
**Evidencia local:** solicitud contextual, rationale y acceso a ajustes  
**Observaciones:** la bandeja in-app debe permanecer operativa.

## 9. Wiring M03–M05

**Caso:** M03 invitaciones, roles, remoción y ownership  
**Entorno:** staging no productivo  
**Fecha:** 2026-07-17  
**Actor técnico:** NO EJECUTADO  
**Resultado:** **NO EJECUTADO**  
**Evidencia local:** triggers 027 auditados  
**Observaciones:** comprobar recipient/organización/dedup/idempotencia.

**Caso:** M04 medidas, apelaciones, verificación y soporte INTERNAL  
**Entorno:** staging no productivo  
**Fecha:** 2026-07-17  
**Actor técnico:** NO EJECUTADO  
**Resultado:** **NO EJECUTADO**  
**Evidencia local:** triggers 027; INTERNAL dirigido a staff asignado  
**Observaciones:** confirmar que requester nunca recibe INTERNAL.

**Caso:** M05 uploads y documentos de verificación  
**Entorno:** staging no productivo  
**Fecha:** 2026-07-17  
**Actor técnico:** NO EJECUTADO  
**Resultado:** **NO EJECUTADO**  
**Evidencia local:** triggers 027 auditados  
**Observaciones:** comprobar disponibilidad solo para staff autorizado.

## 10. Realtime

**Caso:** publicación, RLS, filas propias y lifecycle de suscripción  
**Entorno:** staging no productivo  
**Fecha:** 2026-07-17  
**Actor técnico:** NO EJECUTADO  
**Resultado:** **NO EJECUTADO — REALTIME PENDIENTE**  
**Evidencia:** no hay implementación ni entorno autorizado para probar una suscripción  
**Observaciones:** fallback refresh/polling activo; no exponer events/outbox/deliveries.

## 11. Calidad local (evidencia disponible)

**Caso:** suite Android local  
**Entorno:** Windows local / debug  
**Fecha:** 2026-07-17  
**Actor técnico:** Auto (Cursor)  
**Resultado:** **PASS LOCAL**  
**Evidencia:** `assembleDebug` SUCCESS; `testDebugUnitTest` 489/0/0; `lintDebug` SUCCESS  
**Observaciones:** no sustituye la validación remota.

## 12. Gate

**RELEASE BLOQUEADO** hasta que staging complete todos los casos remotos con PASS, historial real, backup, evidencia y sin usar producción.
