# LEOVER — M06 Auditoría inicial (Notificaciones)

**Módulo:** M06 — Notificaciones  
**Etapa:** 1 — Auditoría y diseño  
**Fecha:** 2026-07-16  
**Rama:** `m06/notificaciones-auditoria`  
**Dependencia:** M05 cerrado a nivel código y calidad local (`M05-cierre-final.md`, commit `80ce3f8e80a89b3043273102b701a5aadca821ad`)  
**Backend oficial:** Supabase (ADR-0001)  
**Alcance:** inventario y diseño; **sin** migraciones, **sin** tablas nuevas, **sin** Firebase nuevo, **sin** dependencias push nuevas, **sin** Edge Functions nuevas, **sin** cron/jobs/WorkManager, **sin** repositorios nuevos, **sin** pantallas, **sin** cambios de funcionalidad, **sin** M07

**Documentos de entrada (orden leído):**

1. `docs/01-producto/D01-Modulos-y-Orden.md`  
2. `docs/02-arquitectura/M00-cierre-final.md` … `M05-cierre-final.md`  
3. `docs/04-calidad/M05-reporte-validacion-staging.md`  
4. `docs/03-modulos/M06-Notificaciones.md`  
5. ADR-0001 … ADR-0005  

---

## 0. Estado Git y calidad

| Ref | Nota |
|-----|------|
| Commit base M05 cierre | `80ce3f8e80a89b3043273102b701a5aadca821ad` |
| Rama | `m06/notificaciones-auditoria` |
| Working tree al crear rama | Limpio salvo spec M06 untracked (`docs/03-modulos/M06-Notificaciones.md`) |
| WIP GPS/mapas/pagos | **No** mezclado (`wip/gps-mapas-pagos` aislada) |
| Merge a `main` | **No** |
| Migraciones creadas/editadas | **Ninguna** |
| Funcionalidad modificada | **Ninguna** (solo este documento + spec de entrada) |
| Username / AuthRepository / `domain/auth` | **Sin cambios** |
| Staging 014–025 | **PENDIENTE DE VALIDACIÓN REMOTA** (bloquea release; no bloquea esta auditoría) |

| Control | Resultado |
|---------|-----------|
| `assembleDebug` | **SUCCESS** |
| `testDebugUnitTest` | **358** tests, **0** failures, **0** errors |
| `lintDebug` | **SUCCESS** |

---

## 1. Hallazgo central

Existe una **base parcial y riesgosa** de notificaciones heredada de fases tempranas:

| Capa | Estado |
|------|--------|
| Bandeja in-app (`notifications` + UI) | **IMPLEMENTADO** (modelo plano, tipos limitados) |
| Tokens FCM (`device_tokens` + cliente) | **IMPLEMENTADO** / **PARCIAL** multi-dispositivo |
| Edge Function `push` + webhook | **PARCIAL** (fuera de migraciones; drift operativo) |
| Preferencias / quiet hours / marketing consent | **AUSENTE** |
| Outbox / deliveries / dead-letter / retry | **AUSENTE** |
| Destinatario server-side tipado | **RIESGO** (`create_notification` + INSERT RLS abiertos) |
| Deep links tipados con revalidación | **PARCIAL** / **AUSENTE** (PendingIntent solo abre `MainActivity`) |
| Cableado M01–M05 (org, moderación, soporte, archivos) | **AUSENTE** (solo social/chat/adopción/servicios vía cliente) |
| WorkManager / AlarmManager / BroadcastReceiver | **AUSENTE** |

M06 debe **gobernar y endurecer** antes de ampliar canales. El gap crítico de seguridad es que **cualquier usuario autenticado puede crear notificaciones para otro usuario** (RPC + RLS).

---

## 2. Inventario Android

| Área | Clasificación | Evidencia |
|------|---------------|-----------|
| Dependencias Firebase Messaging | **IMPLEMENTADO** | `libs.versions.toml` (`firebaseBom` 33.12.0), `app/build.gradle.kts`, `google-services.json` |
| Otras libs push (OneSignal, etc.) | **AUSENTE** | — |
| `FirebaseMessagingService` | **IMPLEMENTADO** | `LeoverFirebaseMessagingService` |
| Token refresh | **IMPLEMENTADO** | `onNewToken` → `PushTokenRegistrar` |
| Persistencia token | **IMPLEMENTADO** | Upsert `device_tokens` (Supabase) / set in-memory (mock) |
| WorkManager | **AUSENTE** | Sin uso |
| AlarmManager | **AUSENTE** | Sin uso |
| BroadcastReceiver notificaciones | **AUSENTE** | Solo servicio FCM |
| `POST_NOTIFICATIONS` | **IMPLEMENTADO** | Manifest + request runtime API 33+ (`MainActivity`) |
| Notification channels | **IMPLEMENTADO** | Canal único `leover_default` |
| PendingIntent | **PARCIAL** | Abre `MainActivity`; **sin** extras de ruta |
| Deep links de notificación | **AUSENTE** / **PARCIAL** | Payload Edge trae `type`/`related_id`; cliente **ignora** |
| NavRoutes | **PARCIAL** | `NOTIFICATIONS = "notifications"` → bandeja |
| Badges / contadores | **IMPLEMENTADO** | Unread en `NotificationsViewModel` / `ProfileViewModel` / Badge UI |
| ViewModels | **IMPLEMENTADO** | `NotificationsViewModel` |
| Repositorios | **IMPLEMENTADO** | `PlatformRepository` + `PlatformSupabaseDataSource` |
| Preferencias de notificación | **AUSENTE** | — |
| Logout limpia tokens | **PARCIAL** / **RIESGO** | `unlinkForCurrentUser` borra **todos** los tokens del usuario |
| Cambio de cuenta | **PARCIAL** | Solo vía logout; mock logout no siempre desvincula |
| Múltiples dispositivos | **PARCIAL** | Schema lo permite; logout all-devices + mock 1 token |
| Logs / PII tokens | **PARCIAL** | Cliente no loguea token; Edge puede devolver prefijo |
| Mocks | **IMPLEMENTADO** | `InMemoryDataStore` notificaciones/tokens |
| Tests dedicados notificaciones | **AUSENTE** | Sin `*Notification*Test*` |
| Inbox UI | **IMPLEMENTADO** | `NotificationsScreen` (marcar leída; sin navegar a recurso) |
| Emisión cliente | **RIESGO** | `NotificationDispatcher` desde ViewModels (chat, amigos, adopción, foster, booking, comunidad) |

### 2.1 Tipos Android actuales

```text
FRIEND_REQUEST, FRIEND_ACCEPTED, CHAT_MESSAGE,
ADOPTION_REQUEST, FOSTER_REQUEST, BOOKING, SIGHTING, SYSTEM
```

No cubren: ACCOUNT, SECURITY, ORGANIZATION, INVITATION, MODERATION, APPEAL, VERIFICATION, SUPPORT, FILES.

---

## 3. Inventario Supabase (migraciones 001–025)

| Artefacto | Origen | Clasificación | Notas |
|-----------|--------|---------------|-------|
| `public.notifications` | `012` | **IMPLEMENTADO** | Plano: type/title/body/related_*; sin categoría/sensibilidad/dedup |
| RLS `notifications` SELECT/UPDATE | `012` | **IMPLEMENTADO** | Solo filas propias |
| RLS `notifications` INSERT | `012` | **RIESGO** | `WITH CHECK (true)` |
| `create_notification(...)` | `012` | **IMPLEMENTADO** / **RIESGO** | `SECURITY DEFINER`; grant `authenticated`; **sin** validar caller vs destinatario |
| `public.device_tokens` | `013` | **IMPLEMENTADO** | Unique `(user_id, token)`; RLS por usuario |
| Preferencias notificación | — | **AUSENTE** | `user_privacy_settings` / `user_consents` no son preferencias push |
| `notification_deliveries` / outbox / DLQ | — | **AUSENTE** | — |
| Triggers dominio → notificación | — | **AUSENTE** | — |
| Realtime `notifications` | — | **AUSENTE** | Realtime en chat/social; bandeja por **polling** Android |
| Cron / pg_cron | — | **AUSENTE** | — |
| Edge Function `push` | `supabase/functions/push` (no SQL) | **PARCIAL** | FCM HTTP v1; webhook DB documentado, no versionado en migraciones |
| Email Auth | Auth nativo | **PARCIAL** | Reset/verify ≠ M06 transaccional |
| Plantillas M06 | — | **AUSENTE** | — |
| Dedup / retry notificaciones | — | **AUSENTE** | Solo unique tokens |

**Migraciones `014`–`025`:** no añaden capacidades M06; M04/M05 escriben audit logs, **no** `notifications`.

---

## 4. Inventario de canales

| Canal | Estado | Uso actual | Gap |
|-------|--------|------------|-----|
| In-app | **IMPLEMENTADO** | Bandeja + unread | Sin filtros/archivo/agrupación/expiración |
| Push FCM | **PARCIAL** | Webhook → Edge → FCM | Sin preferencias, deep link, retry, purga tokens inválidos |
| Email | **AUSENTE** (M06) / Auth nativo | Reset/verify | No marketing; no invitaciones tipadas M06 |
| Local (Alarm/WorkManager) | **AUSENTE** | — | Solo futuro recordatorios usuario |

---

## 5. Inventario de deep links

| Tipo | Estado | Evidencia |
|------|--------|-----------|
| Auth callback | **IMPLEMENTADO** | `com.comunidapp.app://login-callback` (M01; no M06) |
| Ruta bandeja | **IMPLEMENTADO** | `NavRoutes.NOTIFICATIONS` |
| Deep link tipado a recurso (caso, ticket, invitación, asset) | **AUSENTE** | Push abre app genérica |
| Allowlist + revalidación de permisos al abrir | **AUSENTE** | Diseño requerido Etapa 2+ |
| Staff / moderación / soporte deep links | **PARCIAL** (nav M04) | No cableados desde notificaciones |

**Regla confirmada (diseño):** una notificación **nunca** concede acceso; el deep link debe revalidar sesión y permisos.

---

## 6. Mapa de eventos M01–M05

Leyenda: destinatario · categoría · prioridad · sensibilidad · canal · deep link · permiso · expiración · dedup key · preferencias · lock-screen.

### 6.1 M01 — Cuenta y seguridad

| Evento | Clasificación | Destinatario | Cat | Pri | Sens | Canal | Deep link | Permiso | Exp | Dedup | Pref | Lock |
|--------|---------------|--------------|-----|-----|------|-------|-----------|---------|-----|-------|------|------|
| Registro / verificación email | **PARCIAL** (Auth email) | Usuario | ACCOUNT | NORMAL | PRIVATE | Email Auth | Auth | Sesión propia | — | email-verify:{uid} | N/A Auth | No body |
| Reset password | **PARCIAL** (Auth) | Usuario | SECURITY | HIGH | SECURITY_CRITICAL | Email Auth | Auth | — | corto | pwd-reset:{uid}:{jti} | N/A | Genérico |
| Cambio contraseña / sesión | **AUSENTE** | Usuario | SECURITY | HIGH | SECURITY_CRITICAL | In-app+Push genérico | Seguridad | Propio | 7d | pwd-changed:{uid}:{tsBucket} | Sí (push) | Genérico |
| Cambio estado SUSPENDED/BANNED | **AUSENTE** | Usuario | ACCOUNT | URGENT | SENSITIVE | In-app+Push genérico | Cuenta | Propio | 30d | status:{uid}:{status}:{histId} | Excepción quiet hours | Genérico |
| Asignación/revocación rol plataforma | **AUSENTE** | Usuario | ACCOUNT | HIGH | PRIVATE | In-app | Perfil/staff | Propio | 14d | role:{uid}:{role}:{action} | Sí | Resumen |
| Eliminación cuenta | **AUSENTE** | Usuario | ACCOUNT | HIGH | SENSITIVE | In-app+Email | — | Propio | 7d | delete:{uid}:{reqId} | Sí | Genérico |

### 6.2 M02 — Perfiles, social, permisos

| Evento | Clasificación | Destinatario | Cat | Pri | Sens | Canal | Deep link | Permiso | Exp | Dedup | Pref | Lock |
|--------|---------------|--------------|-----|-----|------|-------|-----------|---------|-----|-------|------|------|
| Solicitud amistad | **IMPLEMENTADO** (cliente) | Destino | SOCIAL | NORMAL | PRIVATE | In-app+Push | Amigos | Propio | 14d | friend-req:{from}:{to} | Sí | Resumen |
| Amistad aceptada | **IMPLEMENTADO** (cliente) | Solicitante | SOCIAL | NORMAL | PRIVATE | In-app+Push | Amigos | Propio | 7d | friend-acc:{a}:{b} | Sí | Resumen |
| Mensaje chat | **IMPLEMENTADO** (cliente) | Peer | MESSAGE | NORMAL | PRIVATE | In-app+Push | Chat* | Propio | 3d | msg:{conv}:{msgId} | Sí | Genérico (no body) |
| Comentario / like / sighting | **PARCIAL** / **MOCK** | Owner | SOCIAL/LOST_FOUND | LOW–NORMAL | PRIVATE | In-app | Post/caso | Propio | 7d | social:{type}:{id}:{actor} | Sí | Resumen |

\* Deep link chat: UI existe; desde push **AUSENTE**.

### 6.3 M03 — Organizaciones e invitaciones

| Evento | Clasificación | Destinatario | Cat | Pri | Sens | Canal | Deep link | Permiso | Exp | Dedup | Pref | Lock |
|--------|---------------|--------------|-----|-----|------|-------|-----------|---------|-----|-------|------|------|
| Invitación creada | **AUSENTE** | Invitee | INVITATION | HIGH | PRIVATE | In-app+Email futuro | Invitación | Propio | TTL invitación | invite:{invId} | Sí | Resumen |
| Invitación aceptada/rechazada | **AUSENTE** | Admin/owner | ORGANIZATION | NORMAL | PRIVATE | In-app | Org miembros | `organization.*` | 14d | invite-resp:{invId} | Sí | Resumen |
| Cambio rol / transfer ownership | **AUSENTE** | Afectado + owners | ORGANIZATION | HIGH | PRIVATE | In-app | Org | Org scoped | 30d | org-role:{org}:{uid}:{action} | Sí | Resumen |
| Org suspendida / verificación | Ver M04 | Miembros/revisores | ORGANIZATION/VERIFICATION | HIGH | SENSITIVE | In-app+Push genérico | Org/cola | Org/staff | 30d | org-status:{org}:{state} | Sí / staff | Genérico |

**Confirmado:** roles M03 solo dentro de su organización; AccountType/`active_modules` no conceden acceso al deep link.

### 6.4 M04 — Moderación, medidas, apelaciones, verificación, soporte

| Evento | Clasificación | Destinatario | Cat | Pri | Sens | Canal | Deep link | Permiso | Exp | Dedup | Pref | Lock |
|--------|---------------|--------------|-----|-----|------|-------|-----------|---------|-----|-------|------|------|
| Reporte creado (ack reporter) | **AUSENTE** | Reporter | MODERATION | LOW | PRIVATE | In-app | Mis reportes | Propio | 14d | report-ack:{reportId} | Sí | Resumen |
| Triage / cola staff | **AUSENTE** | Staff con permiso | MODERATION | NORMAL | SENSITIVE | In-app | Cola | `moderation.view` | 7d | triage-queue:{reportId}:{status} | Staff prefs | Genérico |
| Medida aplicada (afectado) | **AUSENTE** | Target user/org | MODERATION | HIGH | SENSITIVE | In-app+Push genérico | Apelación/info | Propio | 90d | action:{actionId} | Excepción política | Genérico |
| Apelación presentada / resuelta | **AUSENTE** | Afectado / staff | APPEAL | HIGH | SENSITIVE | In-app | Apelación | Propio / `review_appeals` | 30d | appeal:{appealId}:{state} | Sí | Genérico |
| Verificación APPROVE/REJECT/MORE_INFO | **AUSENTE** | Org owners | VERIFICATION | HIGH | SENSITIVE | In-app | Org verificación | Org update | 30d | verif:{org}:{reviewId} | Sí | Genérico |
| Ticket soporte creado / respuesta | **AUSENTE** | Requester / staff | SUPPORT | NORMAL–HIGH | PRIVATE/SENSITIVE | In-app | Ticket | Propio / `support.view` | 30d | support:{ticketId}:{msgId} | Sí | Genérico |
| Mensaje INTERNAL / adjunto INTERNAL | **AUSENTE** (y **prohibido** a requester) | Solo staff | SUPPORT | NORMAL | SENSITIVE | In-app staff | Ticket staff | `support.view_sensitive` | 30d | support-int:{msgId} | Staff | Genérico |

**Confirmado:** notas y adjuntos INTERNAL **no** se notifican al solicitante; contenido sensible no completo en push.

### 6.5 M05 — Archivos y uploads

| Evento | Clasificación | Destinatario | Cat | Pri | Sens | Canal | Deep link | Permiso | Exp | Dedup | Pref | Lock |
|--------|---------------|--------------|-----|-----|------|-------|-----------|---------|-----|-------|------|------|
| Upload completado (propio) | **FUTURO** / **AUSENTE** | Actor | SYSTEM | LOW | PRIVATE | In-app opcional | Recurso | Propio | 3d | upload-ok:{sessionId} | Sí | Resumen |
| Upload fallido | **FUTURO** | Actor | SYSTEM | NORMAL | PRIVATE | In-app | Reintento UI | Propio | 3d | upload-fail:{sessionId} | Sí | Resumen |
| Evidencia adjuntada a caso | **AUSENTE** | Staff caso | MODERATION | NORMAL | SENSITIVE | In-app | Caso | `moderation.*` | 14d | evidence:{assetId} | Staff | Genérico |
| Signed URL / acceso | **AUSENTE** (y **prohibido** en payload) | — | — | — | — | — | — | — | — | — | — | — |

**Confirmado:** signed URLs y tokens **nunca** en notificaciones ni logs.

### 6.6 Emisiones actuales (cliente)

| Call site | Tipo | Clasificación |
|-----------|------|---------------|
| `ChatViewModel` | CHAT_MESSAGE | **IMPLEMENTADO** / **RIESGO** (cliente elige destinatario) |
| `SearchFriendsViewModel` / `FriendRequestsViewModel` / `UserPublicProfileViewModel` | FRIEND_* | **IMPLEMENTADO** / **RIESGO** |
| `AdoptionDetailViewModel` | ADOPTION_REQUEST | **IMPLEMENTADO** / **RIESGO** |
| Foster / bookings / community | FOSTER/BOOKING/… | **PARCIAL** |
| M03 invitaciones / M04 / M05 | — | **AUSENTE** |

---

## 7. Categorías, prioridades y sensibilidad (propuesta)

Categorías iniciales (spec M06): ACCOUNT, SECURITY, ORGANIZATION, INVITATION, MODERATION, APPEAL, VERIFICATION, SUPPORT, PET, ADOPTION, FOSTER, SHELTER, LOST_FOUND, DONATION, EVENT, SOCIAL, MESSAGE, SERVICE, APPOINTMENT, PAYMENT, MARKETPLACE, SYSTEM, OTHER.

| Prioridad | Uso |
|-----------|-----|
| LOW | Informativos, acks |
| NORMAL | Social, soporte estándar |
| HIGH | Invitaciones, verificaciones, cambios de rol |
| URGENT | Seguridad / bienestar animal / incidentes aprobados |

| Sensibilidad | Push / lock screen |
|--------------|--------------------|
| PUBLIC_SUMMARY | Texto descriptivo corto |
| PRIVATE | Resumen sin PII |
| SENSITIVE | Texto genérico (“Tenés una actualización…”) |
| SECURITY_CRITICAL | Genérico + excepción quiet hours según política |

---

## 8. Preferencias (propuesta)

Modelo conceptual `notification_preferences`:

- por usuario + categoría + canal (in-app / push / email);
- defaults seguros (in-app ON; push ON no-sensible; email OFF salvo SECURITY/INVITATION);
- quiet hours + timezone IANA del usuario;
- marketing **OFF** hasta consentimiento explícito;
- seguridad crítica puede bypassear quiet hours solo con política documentada.

---

## 9. Tokens e instalaciones (propuesta)

Evolucionar `device_tokens` → `notification_device_installations`:

- `user_id`, `installation_id`, `token`, `platform`, `app_version`, `enabled`, `last_seen_at`, `revoked_at`;
- logout **solo** revoca la instalación actual (no todas);
- refresh rota token; invalid FCM → revoke;
- múltiples dispositivos OK;
- bandeja in-app **única por usuario** (no duplicar por dispositivo).

---

## 10. Riesgos de seguridad y privacidad

| ID | Riesgo | Severidad | Mitigación propuesta (Etapa 2+) |
|----|--------|-----------|----------------------------------|
| R1 | `create_notification` + INSERT RLS permiten notificar a terceros | **Crítica** | Destinatario solo server-side; revoke INSERT cliente; RPC restringida / service role outbox |
| R2 | Logout borra tokens de **todos** los dispositivos | **Alta** | Revoke por `installation_id` |
| R3 | Push sin preferencias ni quiet hours | **Alta** | Preferencias + timezone |
| R4 | Deep link ausente / sin revalidación | **Alta** | Allowlist tipada + gates M02/M03/M04 |
| R5 | Contenido sensible posible en title/body push | **Alta** | Plantillas por sensibilidad |
| R6 | Android emite notificaciones (Dispatcher) | **Alta** | Migrar a outbox server-side |
| R7 | INTERNAL podría notificarse por error | **Alta** | Regla explícita: nunca requester |
| R8 | Webhook push no versionado en migraciones | **Media** | Documentar + IaC / checklist staging |
| R9 | Prefijo token en respuestas Edge | **Baja** | Sanitizar respuestas |
| R10 | Polling bandeja (no Realtime) | **Media** | Realtime o outbox + sync M07 |

**Confirmaciones de diseño (obligatorias):**

- Una notificación **nunca** concede acceso al recurso.  
- Destinatario **server-side**.  
- Android **no** crea notificaciones para otro usuario.  
- Deep links: allowlist + revalidar permisos.  
- AccountType / `active_modules` sin autoridad.  
- M03 scoped a su org.  
- INTERNAL no al solicitante.  
- Push sin contenido sensible completo.  
- Tokens / signed URLs fuera de logs y payloads.  
- Logout limpia estado de la instalación.  
- Bandeja in-app no se duplica por dispositivo.  
- Reintentos idempotentes.  
- Quiet hours con TZ del usuario.  
- Seguridad crítica con reglas especiales.  
- Marketing solo con consentimiento.

---

## 11. Duplicaciones y gaps

### Duplicaciones

- Emisión cliente vs futuro outbox server (doble riesgo de duplicar eventos).  
- Tipos Android (`NotificationType`) vs categorías M06 (desalineados).  
- Auth email vs futuro email M06 (no unificar sin diseño).

### Gaps principales

1. Seguridad de creación de notificaciones.  
2. Preferencias / quiet hours / marketing consent.  
3. Outbox + deliveries + DLQ + retry.  
4. Cableado M03/M04/M05.  
5. Deep links tipados.  
6. Canales por categoría.  
7. Tests de notificaciones.  
8. Observabilidad (M07).  
9. Multi-device logout correcto.  
10. Staging 014–025 pendiente (release).

---

## 12. Propuesta de modelo (sin implementar)

```text
notifications                  -- evento canónico (plantilla + payload tipado)
notification_recipients        -- estado por usuario (read/archive/delete lógico)
notification_deliveries        -- intento por canal/dispositivo
notification_preferences       -- opt-in/out + quiet hours + TZ
notification_device_installations
notification_templates         -- title_key/body_key + sensibilidad
notification_outbox            -- cola de procesamiento idempotente
notification_dead_letters      -- fallos agotados
```

Estados recipient: `PENDING`, `DELIVERED_IN_APP`, `READ`, `ARCHIVED`, `DELETED`.  
Estados delivery: `QUEUED`, `SENDING`, `SENT`, `FAILED`, `SKIPPED_PREF`, `SKIPPED_QUIET`, `DEAD`.

---

## 13. Propuesta de outbox

1. Dominio (RPC M01–M05) escribe evento → outbox (misma transacción cuando sea posible).  
2. Worker/Edge consume outbox: calcula destinatarios, aplica preferencias, crea `notifications` + `recipients`, encola deliveries.  
3. `deduplication_key` unique parcial evita duplicados.  
4. Idempotency-key por evento de origen.  
5. Sin cron en Etapa 1; Etapa 3+ define mecanismo aprobado.

---

## 14. Propuesta entrega in-app

- Fuente de verdad: `notifications` + `notification_recipients`.  
- UI: bandeja, unread, filtros por categoría, mark one/all, archive.  
- Observación: Realtime o sync incremental (hoy polling).  
- Multi-device: misma bandeja; deliveries push independientes.

---

## 15. Propuesta push

- Canal futuro gobernado por preferencias y sensibilidad.  
- Payload mínimo: `notification_id`, `category`, `deep_link_type`, `resource_id` (sin body sensible).  
- FCM ya presente: **reutilizar y endurecer**, no añadir SDKs en Etapa 1.  
- Invalid token → revoke instalación.  
- No asumir webhook estable sin evidencia staging.

---

## 16. Deduplicación, idempotencia, retry, dead-letter

| Mecanismo | Propuesta |
|-----------|-----------|
| Dedup | `deduplication_key` unique por recipient/ventana |
| Idempotencia | key de origen (RPC/event id) en outbox |
| Retry | backoff exponencial acotado; solo fallos transitorios |
| Dead-letter | tras N intentos; alerta M07; reproceso manual staff |
| Quiet hours | defer delivery push/email; in-app puede persistir |

---

## 17. Observabilidad (coordinada con M07)

Eventos M06 (sin PII/token/signed URL): generado, destinatarios, preferencia aplicada, canal, intento, resultado, reintento, token revocado, deep link abierto (éxito/deny).

M07 es propietario transversal; M06 define el catálogo.

---

## 18. Decisiones que requieren aprobación

1. ¿Endurecer `create_notification`/RLS en Etapa 2 o 3 como hotfix de seguridad?  
2. ¿Migrar emisiones cliente (`NotificationDispatcher`) a outbox server en una sola etapa?  
3. ¿FCM Edge Function oficial vs reemplazo?  
4. ¿Realtime vs polling para bandeja?  
5. ¿Email transaccional (Resend/Supabase) y alcance INVITATION/SECURITY?  
6. ¿Excepciones quiet hours para SECURITY_CRITICAL y medidas M04?  
7. ¿Timezone: perfil usuario vs dispositivo?  
8. ¿Conservar tabla `notifications` 012 con migración evolutiva o modelo nuevo paralelo?

---

## 19. Archivos futuros (no crear ahora)

```text
domain/notifications/*Contracts*
data/repository/*Notification*Repository*
supabase/migrations/0xx_m06_notifications_foundation.sql
supabase/functions/... (solo tras diseño)
ui/screens/notifications/* (evolución)
docs/02-arquitectura/M06-etapa-*-cierre.md
docs/04-calidad/M06-*
```

---

## 20. Plan por etapas

| Etapa | Alcance |
|-------|---------|
| **1** | Auditoría y diseño (este documento) — **STOP** |
| **2** | Contratos: categorías, sensibilidad, prioridad, deep links, preferencias, mocks, tests de dominio |
| **3** | Persistencia: modelo/outbox/recipients, RLS endurecido, entrega in-app server-side, sin push nuevo |
| **4** | Push gobernado + UI operativa (canales, deep links, logout por instalación) |
| **5** | Calidad, staging, cierre; observabilidad M07 alineada |

**No** iniciar Etapa 2 en esta ejecución.

---

## 21. ADRs relevantes

| ADR | Implicación M06 |
|-----|-----------------|
| 0001 Supabase | Backend oficial; Auth email ≠ M06 completo |
| 0002 Monolito modular | Notificaciones como capa transversal en app actual |
| 0003 DI / providers | `DataProvider` / flags; sin Hilt obligatorio |
| 0004 Acceso datos | PostgREST/RPC; sin segundo cliente |
| 0005 Ambientes/secretos | FCM secrets solo server; no en APK logs |

---

## 22. Staging

```text
PENDIENTE DE VALIDACIÓN REMOTA
```

Migraciones `014`–`025` sin evidencia de aplicación en staging.  
Bloquea **release** de LeoVer; **no** bloquea esta auditoría ni el diseño M06.

---

## 23. Checklist Etapa 1

- [x] Commit base verificado  
- [x] Rama `m06/notificaciones-auditoria`  
- [x] Sin mezcla GPS/mapas/pagos  
- [x] Sin migraciones / Firebase nuevo / pantallas / repos  
- [x] Inventario Android y Supabase  
- [x] Canales y deep links  
- [x] Mapa eventos M01–M05 clasificado  
- [x] Riesgos y propuesta de modelo/outbox/push  
- [x] Plan por etapas  
- [x] Calidad local 358 / assemble / lint  
- [x] Staging declarado honestamente  
- [x] Username/auth intactos  

---

## 24. Parada

Etapa 1 **cerrada a nivel de auditoría**.

**No** iniciar Etapa 2.  
**No** crear migraciones ni Edge Functions.  
**No** merge a `main`.  
**No** iniciar M07.  
**No** corregir username/auth en esta rama.  
**No** commit en este informe (documento listo para revisión).
