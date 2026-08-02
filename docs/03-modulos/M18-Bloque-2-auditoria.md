# M18 Bloque 2 — Auditoría

## HEAD inicial

Referencia post Bloque 1 M18 — fundación local/mock operativa.

## Autoridades auditadas

| Dominio | Autoridad | M18 B2 |
|---------|-----------|--------|
| Usuarios/sesión | M01 | `created_by`; no expuesto en JSON público |
| Permisos plataforma | M02 | Moderadores vía `_m18_is_moderator` |
| Organizaciones | M03 | `organization_id` FK; `has_org_permission` |
| Moderación | M04 | `M18EventModerationAdapter` → `ModerationRepository.createReport` |
| Archivos | M05 | `cover_image_ref` (ref, sin binarios) |
| Notificaciones | M06 | Allowlist **no ampliada**; `m18_schedule_reminder` → infra unavailable |
| Mascotas | M08 | `pet_id` FK opcional + nombre público |
| Refugios | M16 | `shelter_profile_id` FK opcional |
| Pagos | M24 | **Sin pagos ni entradas** |

## Migración 058

**Nombre exacto:** `supabase/migrations/058_m18_community_events_and_registrations.sql`

**Tablas:**
- `m18_community_events`
- `m18_event_registrations`
- `m18_event_reminders` (minimal)

**Permisos SQL:** `event.view`, `event.manage` insertados en `organization_permissions`.

**Tipos org elegibles:** SHELTER, RESCUE_GROUP, NGO, **TRAINING_CENTER** (alineado a `M18_ELIGIBLE_ORGANIZATION_TYPES`).

## Superficie pública sanitizada

JSON público **sin** `organization_id`, `created_by`, `user_id`, `pet_id`, `shelter_profile_id`.

Agregados de cupo: `registered_count`, `waitlist_count`, `available_spots`, `is_registration_open`.

## Riesgo financiero

Ninguno — eventos gratuitos; sin tablas de pago.

## M06

M18 no está en allowlist M06. Bloque 2 **no bloqueado** — RPC `m18_schedule_reminder` y repositorio remoto devuelven `M18_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE`.

## Alcance Bloque 2

- Migración 058 creada, **SQL no aplicado**
- `SupabaseM18RemoteDataSource`, `SupabaseM18EventRepository`
- RLS + RPCs superficie pública sanitizada
- Permisos M03 Kotlin + SQL (`OrganizationPermissionCode.EVENT_*`)
- Adaptador M04 mínimo (`M18EventModerationAdapter`)
- Mock operativo (`MockM18EventRepository` conservado)
- **Bloque 3 no iniciado** hasta cierre B2
