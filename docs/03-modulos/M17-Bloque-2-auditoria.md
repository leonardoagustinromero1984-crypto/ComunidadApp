# M17 Bloque 2 — Auditoría

## HEAD inicial

`d09735411d44a6dec526f6bbe4d896ca0d7a41cd` — `feat(m17): establish donation campaigns foundation`

## Corrección conteos Bloque 1

| Informe Bloque 1 | Real |
|------------------|------|
| "13 código + 4 docs" | **8 archivos Kotlin/test + 4 docs = 12 nuevos** |
| "5 modificados" | **6 modificados** |

**Archivos nuevos Bloque 1 (8):** `M17DonationModels.kt`, `M17DonationValidators.kt`, `M17DonationRepositories.kt`, `M17DonationErrorMapper.kt`, `M17DonationViewModels.kt`, `M17DonationScreens.kt`, `M17NavGraph.kt`, `M17DonationFoundationTest.kt`

**Docs nuevos (4):** auditoría inicial, matriz funcional, arquitectura, Bloque-1-validacion

**Modificados (6):** `DataProvider.kt`, `ComunidappNavGraph.kt`, `NavRoutes.kt`, `SumateScreen.kt`, `SumateTabContent.kt`, `D01-Modulos-y-Orden.md`

## Autoridades auditadas

| Dominio | Autoridad | M17 B2 |
|---------|-----------|--------|
| Usuarios/sesión | M01 | `created_by`; no expuesto en público |
| Permisos plataforma | M02 | Moderadores vía `_m17_is_moderator` |
| Organizaciones | M03 | `organization_id` FK; `has_org_permission` |
| Moderación | M04 | `M17CampaignModerationAdapter` → `ModerationRepository.createReport` |
| Archivos | M05 | `cover_image_ref`, `gallery_image_refs` (refs, sin binarios) |
| Notificaciones | M06 | Allowlist **no ampliada**; hooks diferidos |
| Mascotas | M08 | `pet_id` FK opcional + nombre público |
| Refugios | M16 | `shelter_profile_id` FK opcional |
| Pagos | M24 | CONFIRMED solo service_role; cliente bloqueado |

## Migración 054

**Nombre exacto:** `supabase/migrations/054_m17_donation_campaigns_and_contributions.sql`

**Tablas:**
- `m17_donation_campaigns`
- `m17_campaign_updates`
- `m17_contributions`

**Sin tabla de imágenes separada** — galería en `gallery_image_refs text[]`.

**Permisos SQL:** `donation.view`, `donation.manage` insertados en `organization_permissions`.

## Riesgo financiero

- Montos `bigint` (unidades mínimas)
- Trigger `_m17_contributions_guard` impide CONFIRMED desde cliente
- `registerMockContribution` remoto → `M17_PAYMENT_INFRASTRUCTURE_UNAVAILABLE`

## M06

M17 no está en allowlist M06. Bloque 2 **no bloqueado** — fallback honesto conservado.

## Alcance Bloque 2

- Migración 054 creada, **SQL no aplicado**
- `SupabaseM17RemoteDataSource`, `SupabaseM17DonationRepository`
- RLS + RPCs superficie pública sanitizada
- Permisos M03 Kotlin + SQL
- Adaptador M04 mínimo
- Mock operativo
- **Bloque 3 no iniciado** hasta cierre B2
