# RC1 — Auditoría migraciones 001–077

**Consulta remota staging (read-only):** `supabase_migrations.schema_migrations`  
**Resultado remoto:** 001–038, 053–077 (62 versiones)  
**Local:** 77 archivos en `supabase/migrations/`

## Resumen

| Métrica | Valor |
|---------|-------|
| Total local | 77 |
| Aplicadas staging | 62 |
| Gap | **039–052** (14 migraciones) |
| Última local/remota | **077** |
| Producción | No afectada en esta etapa |

## Inventario por rango

| Rango | Módulo(s) | Local | Staging | Riesgo |
|-------|-----------|-------|---------|--------|
| 001–012 | M00 fundación, legacy | ✓ | ✓ | Bajo |
| 013–028 | M06 notificaciones | ✓ | ✓ | Bajo |
| 029–034 | M07 observabilidad | ✓ | ✓ | Bajo |
| 035–038 | M08/M09 | ✓ | ✓ | Bajo |
| **039–052** | M09–M14 | ✓ | **✗** | **Alto (deuda)** |
| 053 | M16 refugios | ✓ | ✓ | Bajo |
| 054–057 | M17 donaciones | ✓ | ✓ | Bajo |
| 058–059 | M18 eventos | ✓ | ✓ | Bajo |
| 060–061 | M19 social | ✓ | ✓ | Bajo |
| 062–063 | M20 mensajería | ✓ | ✓ | Bajo |
| 064–065 | M21 reputación | ✓ | ✓ | Bajo |
| 066–067 | M22 prestadores | ✓ | ✓ | Bajo |
| 068–069 | M23 reservas | ✓ | ✓ | Bajo |
| 070–071 | M25 marketplace | ✓ | ✓ | Bajo |
| 072–074 | M26 IA | ✓ | ✓ | Bajo |
| 075–077 | M27 integraciones | ✓ | ✓ | Bajo |

## Detalle 053–077 (staging confirmado)

| # | Archivo | Módulo |
|---|---------|--------|
| 053 | m16_shelter_profiles_and_public_access | M16 |
| 054 | m17_donation_campaigns_and_contributions | M17 |
| 055 | m17_in_kind_volunteering_and_transparency | M17 |
| 056 | m17_fix_moderator_helper | M17 |
| 057 | m17_fix_volunteer_public_list | M17 |
| 058 | m18_community_events_and_registrations | M18 |
| 059 | m18_event_operations_and_attendance | M18 |
| 060 | m19_social_posts_and_engagement | M19 |
| 061 | m19_social_feed_media_and_moderation | M19 |
| 062 | m20_messaging_conversations_and_messages | M20 |
| 063 | m20_messaging_operations_and_privacy | M20 |
| 064 | m21_reputation_reviews_and_verifications | M21 |
| 065 | m21_review_operations_and_verification_workflows | M21 |
| 066 | m22_service_providers_and_catalog | M22 |
| 067 | m22_branch_coverage_radius_check_fix | M22 |
| 068 | m23_scheduling_availability_and_bookings | M23 |
| 069 | m23_booking_operations_and_concurrency | M23 |
| 070 | m25_marketplace_catalog_cart_and_orders | M25 |
| 071 | m25_marketplace_operations_inventory_and_returns | M25 |
| 072 | m26_ai_matching_duplicates_assistance_recommendations | M26 |
| 073 | m26_ai_operations_review_and_safety | M26 |
| 074 | m26_review_queue_permission_fix | M26 |
| 075 | m27_integrations_webhooks_oauth_limits_sandbox | M27 |
| 076 | m27_integration_operations_security_and_delivery | M27 |
| 077 | m27_idempotency_stale_resource_fix | M27 |

## Observaciones

- M24: sin migraciones (pospuesto).
- 039–052: ver `RC1-deuda-migraciones-039-052.md`.
- No se creó migración 078 en RC1.
- No se ejecutó `db push` ni `migration repair`.

## Veredicto

Migraciones **001–077 auditadas**. Staging alineado con M16–M27. Gap 039–052 documentado sin aplicar.
