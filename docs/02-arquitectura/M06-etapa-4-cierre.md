# M06 — Cierre Etapa 4: Push gobernado, deep links, preferencias e instalaciones

**Fecha:** 2026-07-17  
**Rama:** `m06/etapa-4-push-deep-links-preferencias-instalaciones`  
**Commit base:** `824a091df9bda80c40492c64486d6d5ecd9d0c06`  
**Producto:** LeoVer  
**Estado:** implementación local sin commit, pendiente de revisión.

---

## 1. Resumen

Etapa 4 reutiliza FCM existente bajo reglas M06: deliveries server-side, payload mínimo tipado, canales Android estables, deep links allowlisted con revalidación, preferencias UI, permiso `POST_NOTIFICATIONS` en contexto, e instalaciones multi-device reales.

No se implementó email. No se inició Etapa 5 ni M07. No hay merge a `main`. No se aplicó producción. Staging `014`–`027` permanece pendiente de validación remota (sin evidencia de apply).

---

## 2. Migración 027

Creada:

```text
supabase/migrations/027_m06_push_delivery_and_installation_hardening.sql
```

No se editaron migraciones `001–026`.

Incluye:

- `m06_plan_push_deliveries`, `m06_claim_push_deliveries`, `m06_mark_delivery_result` (service_role);
- registro/rotación de instalación con `token_reference` sin devolverlo al cliente;
- `m06_emit_domain_notification` (enqueue + materialize + plan push, best-effort);
- triggers server-derived M03 / M04 / M05;
- índice único push por notificación + instalación;
- `device_tokens` legacy conservado; envío Etapa 4 prioriza instalaciones para evitar doble push.

---

## 3. Edge Function `push`

Endurecida `supabase/functions/push/index.ts`:

- autenticación con service role;
- claim de deliveries M06 (`m06_claim_push_deliveries`);
- no acepta `userId` / token / title / body arbitrarios desde Android;
- credenciales FCM solo server-side;
- actualiza resultado vía `m06_mark_delivery_result`;
- revoca instalación ante token inválido;
- logs/respuestas sanitizados;
- no expone token ni provider message ID al cliente Android;
- copy genérico para sensibilidad alta (también en claim SQL).

---

## 4. Canales Android

Estables (no uno por notificación):

| ID | Uso |
|---|---|
| `leover_security` | cuenta / seguridad |
| `leover_organizations` | organizaciones / invitaciones |
| `leover_moderation_support` | moderación / verificación / soporte |
| `leover_pets_adoptions` | mascotas / adopciones / casos |
| `leover_social_messages` | social / mensajes |
| `leover_system` | sistema / desconocido |
| `leover_default` | compatibilidad legacy |

Canal desconocido → `leover_system`. No se borran canales configurados por el usuario.

---

## 5. Deep links

Allowlist operativa: `NOTIFICATIONS_INBOX`, `PROFILE`, `ORGANIZATION`, `ORGANIZATION_INVITATION`, `MODERATION_QUEUE`, `MODERATION_CASE`, `MODERATION_APPEAL`, `ORGANIZATION_VERIFICATION`, `SUPPORT_TICKET`, `PET`, `ADOPTION`, `LOST_FOUND_CASE`, `FILE_RESOURCE`, `CHAT`, `SAFE_HOME`.

Al abrir: sesión, recurso, permiso, organización y sensibilidad se revalidan. Staff requiere permisos M02/M04. Deny → `SAFE_HOME` o bandeja. Doble tap / reentrega no re-navega. Logout y cambio de cuenta limpian navegación pendiente.

Componentes: `NotificationDeepLinkRouter`, `NotificationPendingNavigationStore`, captura en `MainActivity`, consumo en `MainScreen`.

---

## 6. Preferencias UI

Pantalla `NotificationPreferencesScreen` + `NotificationPreferencesViewModel`:

- push on/off por categoría;
- quiet hours, días y timezone;
- marketing OFF por defecto;
- categorías in-app obligatorias no desactivables;
- explicación de críticas;
- email solo como “Próximamente”;
- estados loading / saving / success / error + retry;
- accesibilidad básica.

Ruta: `notification_preferences` desde la bandeja.

---

## 7. Permiso Android

`NotificationPermissionCoordinator`:

- no se pide al iniciar la app (`MainActivity` ya no auto-lanza el prompt);
- explicación previa en preferencias;
- maneja denegación y “no volver a preguntar” con acceso a ajustes;
- no bloquea la app; bandeja in-app sigue disponible.

---

## 8. Instalaciones

`NotificationInstallationCoordinator` (+ facade `PushTokenRegistrar`):

- `installationId` estable;
- registro post-login;
- rotación en `onNewToken`;
- revoke solo instalación actual al logout;
- cambio de usuario desvincula y reasocia;
- token inválido → permanente + revoke server-side;
- raw token no expuesto en modelos/logs públicos;
- evita doble registro por fingerprint;
- si M06 registra OK, no escribe `device_tokens` (evita doble push).

---

## 9. Deliveries

Estados conectados: `PENDING`, `PROCESSING`, `DELIVERED`, `FAILED_RETRYABLE`, `FAILED_PERMANENT`, `SKIPPED_PREFERENCE`, `SKIPPED_PERMISSION`, `SKIPPED_EXPIRED`, `SKIPPED_QUIET_HOURS`, `CANCELLED`, `DEAD_LETTER`.

Reglas: claim atómico, un delivery por instalación/canal, retry transitorio, token inválido permanente + revoke, preferencia → skip, quiet hours → skip/defer, expirado → skip, máximo → dead-letter. Push entregado **no** marca `READ`.

---

## 10. Wiring M03–M05

| Módulo | Estado | Notas |
|---|---|---|
| M03 invitaciones / membresía / ownership | **IMPLEMENTADO** | triggers en 027 |
| M04 medidas / apelaciones / verificación / tickets | **IMPLEMENTADO** | INTERNAL solo staff |
| M05 upload / docs verificación staff | **IMPLEMENTADO** | best-effort; depende de tablas M05 |
| Social / chat / amistad / booking cliente | **PENDIENTE** | sin evento server-derived seguro |

Recipients siempre server-side. Idempotency/dedup keys estables. Errores de notificación no abortan mutaciones de dominio.

---

## 11. NotificationDispatcher

Conservado como compatibilidad. Inventario de call sites documentado en `remainingClientCallSites()`.

- no envía FCM desde ViewModels;
- no reactiva creación cross-user;
- con Supabase, create cliente denegado y no simula éxito;
- deuda social/chat/amistad/booking permanece explícita.

---

## 12. Realtime

**PENDIENTE.** No se afirmó Realtime. Se mantiene refresh/polling vía `NotificationInboxRefreshCoordinator` + observación legacy de bandeja. Suscripción Realtime requiere validación RLS y cierre en logout/cambio de cuenta antes de afirmarse.

---

## 13. Android — componentes Etapa 4

- `NotificationPushPayloadParser`
- `NotificationChannelRegistry`
- `NotificationDeepLinkRouter`
- `NotificationPendingNavigationStore`
- `NotificationPermissionCoordinator`
- `NotificationPreferencesViewModel` / `NotificationPreferencesScreen`
- `NotificationInstallationCoordinator`
- `NotificationInboxRefreshCoordinator`
- `NotificationUiErrorMapper`

Adaptados: `LeoverFirebaseMessagingService`, `PushTokenRegistrar`, `LeoverNotificationHelper`, `MainActivity`, navegación, `NotificationsViewModel` / `NotificationsScreen`, repositorios de instalación (token reference), DataProvider mocks existentes.

---

## 14. Calidad local

- `assembleDebug`: SUCCESS  
- `testDebugUnitTest`: **480 tests, 0 failures, 0 errors**  
- `lintDebug`: SUCCESS  

Baseline Etapa 3: 440. Agregados ~40 tests Etapa 4.

---

## 15. Staging

Migraciones `014`–`027`: **pendientes de validación remota**. No se afirma apply en staging ni producción.

---

## 16. Auth / username

Intactos:

- `AuthRepository`
- `domain/auth`
- `UsernameValidators`

Sin cambios en esta etapa.

---

## 17. Parada

Etapa 4 queda implementada localmente y documentada, **sin commit**.

No iniciar Etapa 5.  
No implementar email.  
No iniciar M07.  
No hacer merge a `main`.  
No aplicar producción.
