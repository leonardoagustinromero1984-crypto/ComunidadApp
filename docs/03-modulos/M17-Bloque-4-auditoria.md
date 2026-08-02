# M17 Bloque 4 — Auditoría migración 054

## HEAD inicial Bloque 4

`d56e9fd40c08d46b8a5bf0f9b0c0f121baba1d87` — `feat(m17): add in-kind donations and volunteering`

## Alcance de esta auditoría

Revisión estática de `supabase/migrations/054_m17_donation_campaigns_and_contributions.sql` **sin aplicar SQL**. Decisiones de Bloque 4 documentadas aquí; migración 054 **no modificada**.

## Checklist SECURITY DEFINER (054)

| # | Criterio | Resultado |
|---|----------|-----------|
| 1 | `search_path = public` en funciones SECURITY DEFINER | PASS — todas las funciones `_m17_*` y `m17_*` |
| 2 | Sin dependencia de search_path controlable por usuario | PASS |
| 3 | `auth.uid()` validado vía `_m17_require_authenticated()` | PASS |
| 4 | `organizationId` y permisos validados en funciones mutables | PASS — `_m17_require_org_perm` |
| 5 | No confía en organizationId solo desde UI | PASS — org obtenida de fila existente |
| 6 | Reutiliza `has_org_permission` / M03 | PASS — `donation.view`, `donation.manage` |
| 7 | Cliente normal no puede establecer CONFIRMED | PASS — trigger `_m17_contributions_guard` |
| 8 | Sin acceso anon directo a tablas internas | PASS — REVOKE + RLS + RPC |
| 9 | EXECUTE revocado de PUBLIC | PASS — grants explícitos anon/authenticated |
| 10 | Grants mínimos | PASS |
| 11 | RPC públicas devuelven JSON sanitizado | PASS — `_m17_public_campaign_json` |
| 12 | Sin SELECT * en superficies públicas | PASS |
| 13 | `provider_reference` no aparece en público | PASS |
| 14 | `idempotency_key` no aparece en público | PASS |
| 15 | DRAFT no es público | PASS — `_m17_campaign_is_public` |
| 16 | COMPLETED/CANCELLED terminales | PASS — `m17_transition_campaign` |
| 17 | REFUNDED no suma al confirmado | PASS — `_m17_confirmed_minor` filtra CONFIRMED |
| 18 | Sin float/real/double para dinero | PASS — `bigint` |

## Defectos bloqueantes en 054

**Ninguno identificado** que requiera editar 054 antes de su primera aplicación.

Observaciones no bloqueantes (registradas, sin cambio en 054):

- Validación remota contra staging pendiente hasta aplicación operativa 054+055.
- M06 allowlist no incluye M17; notificaciones diferidas (documentado en Bloque 4).

## Autoridades Bloque 4 confirmadas

| Dominio | Autoridad | Uso M17 B4 |
|---------|-----------|------------|
| Identidad donante/voluntario | M01 | `contributor_user_id`, `applicant_user_id`; sin copiar email/teléfono |
| Organizaciones | M03 | FK `organization_id`; `has_org_permission` |
| Refugios | M16 | FK opcional `shelter_profile_id` |
| Archivos | M05 | `image_ref`, `public_receipt_file_ref` (refs) |
| Moderación | M04 | `M17CampaignModerationAdapter` extendido |
| Ubicación | M10 | Solo `public_location_text` aproximado |
| Pagos | M24 | Sin checkout ni CONFIRMED cliente |
| Tránsito | M15 | Oportunidades voluntario ≠ foster placement |

## Migración 055

**Nombre:** `supabase/migrations/055_m17_in_kind_volunteering_and_transparency.sql`

**Estado:** creada, **no aplicada**.

**Semántica `quantity_committed`:** suma pledges en estado `ACCEPTED` + `DELIVERED` (trigger `_m17_inkind_pledge_changed`).

**Cobertura pública:** `quantity_delivered` = suma pledges `DELIVERED`; `coverage_percent` basado en entregado vs `quantity_needed`.

## Decisión: no modificar 054

054 permanece intacta. Toda extensión Bloque 4 en 055 únicamente.

## M06

Allowlist **no ampliada**. Eventos futuros documentados en validación Bloque 4:

- pledge creado / aceptado / entrega registrada
- postulación creada / aceptada / rechazada
- oportunidad completada
- transparencia publicada

Fallback: `M17_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE`.

## M04

Adaptador extendido con targets:

- `M17_IN_KIND_NEED`
- `M17_VOLUNTEER_OPPORTUNITY`
- `M17_TRANSPARENCY_REPORT`
- `M17_IN_KIND_NEED_IMAGE`

Sin cola paralela; reutiliza `ModerationRepository.createReport`.

## Privacidad

Superficies públicas 055 omiten: `organization_id`, `created_by`, `contributor_user_id`, `applicant_user_id`, `internal_notes`, mensajes privados, `availability_summary`, datos de moderación internos.

## Pendientes post-Bloque 4

- Aplicación operativa 054 + 055 en staging (ver `docs/05-operacion/M17-aplicacion-migraciones-054-055-supabase.md`)
- Validación remota RLS/RPC
- Cierre operativo global M17 (Bloque 5 no iniciado)
- Pagos reales M24
