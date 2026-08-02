# M18 Bloque 5 — Auditoría paridad remota y cierre

**Fecha:** 2026-08-02  
**Entorno:** Supabase staging `wyst****mizz` (no producción)

## Matriz dominio → persistencia

| # | Elemento | Kotlin | Mock | 058 | 059 | Remoto |
|---|---------|--------|------|-----|-----|--------|
| 1 | Evento | ✓ | ✓ | ✓ | — | ✓ |
| 2 | Org propietaria | ✓ | ✓ | FK | — | ✓ |
| 3 | Estado evento | ✓ | ✓ | CHECK | — | ✓ |
| 4 | Capacidad | ✓ | ✓ | CHECK | — | ✓ |
| 5 | Inscripción | ✓ | ✓ | ✓ | — | ✓ |
| 6 | Registro único user/evento | ✓ | ✓ | UNIQUE | — | ✓ |
| 7 | Lista de espera | ✓ | ✓ | ✓ | — | ✓ |
| 8 | Orden waitlist | ✓ | ✓ | registered_at ASC | — | ✓ |
| 9 | Cancelación | ✓ | ✓ | RPC | — | ✓ |
| 10 | Promoción | ✓ | ✓ | _promote | RPC pública | ✓ |
| 11 | Check-in | ✓ | ✓ | RPC | — | ✓ |
| 12 | Asistencia ATTENDED | ✓ | ✓ | — | CHECK+RPC | ✓ |
| 13 | NO_SHOW | ✓ | ✓ | CHECK | RPC | ✓ |
| 14 | REJECTED | ✓ | ✓ | — | CHECK+RPC | ✓ |
| 15 | Timestamps | ✓ | ✓ | ✓ | attended_at | ✓ |
| 16 | Idempotencia | ✓ | ✓ | RPC | — | ✓ |
| 17 | Moderación | ✓ | M04 | moderation_status | fix moderator | ✓ |
| 18 | Ubicación M10 | ✓ | ✓ | public_location_text | — | ✓ |
| 19 | Archivos M05 | ✓ | ref | cover_image_ref | — | ✓ |
| 20 | Privacidad | ✓ | sanitizer | RPC sanitizadas | revoke anon | ✓ |

## Decisión 059

**REQUERIDA** — 058 no cubría:

- Estados `ATTENDED`, `REJECTED`
- RPC `m18_mark_attendance`, `m18_mark_no_show`, `m18_reject_registration`, `m18_promote_next_waitlisted`
- Columna `attended_at`
- Rechazo evento pasado en registro
- Corrección `_m18_is_moderator` (idempotente)
- Revoke anon en tablas

## Correcciones pre-aplicación 058

Defectos críticos corregidos en 058 **antes de primera aplicación** (nunca aplicada en staging con bug):

- `_m18_is_moderator` → `user_has_active_role`
- Orden parámetros `m18_create_event` / `m18_update_event_details`

## Migraciones aplicadas staging

| Versión | Estado |
|---------|--------|
| 058 | Aplicada |
| 059 | Aplicada |
