# M11 — Campañas, insumos y red de ayuda (arquitectura bloque 2)

LeoVer M11 Bloque 2: campañas institucionales **no monetarias**, pedidos de insumos y compromisos/recepciones de aportes físicos. Sin CBU, alias, tarjetas ni Mercado Pago.

## Integración

| Módulo | Uso |
|--------|-----|
| M03 | Permisos `shelter.campaign.*`, `shelter.supply.*`, `shelter.contribution.*` |
| M05 | Referencias seguras `m05://` y `file_asset:` para portadas y evidencia |
| M06 | Hooks preparados en dominio (`CAMPAIGN_ACTIVATED`, `CONTRIBUTION_PLEDGED`, etc.); **sin push** en este bloque |
| M07 | Auditoría best-effort vía catálogo + eventos en mock store |
| Bloque 1 M11 | Requiere `ShelterProfile` **ACTIVE** para activar campaña o publicar pedidos |

## Modelos

- `ShelterCampaign`, `ShelterCampaignPublicListing`, `ShelterCampaignUpdate`
- `ShelterSupplyRequest`, `ShelterSupplyRequestPublicListing`
- `ShelterSupplyContribution`
- Enums: categoría, visibilidad (`PUBLIC` / `INTERNAL`), estados de campaña, pedido y aporte

## Estados — campaña

```text
DRAFT → ACTIVE ↔ PAUSED → COMPLETED | CANCELLED
```

- Activar exige refugio operativo (`ACTIVE`).
- `COMPLETED` bloqueado si hay pedidos abiertos ligados a la campaña.
- `CANCELLED` cancela pedidos abiertos y aportes no recibidos.

## Estados — pedido de insumos

```text
DRAFT → OPEN → PARTIALLY_COMMITTED → FULLY_COMMITTED
              → PARTIALLY_RECEIVED → FULFILLED
              → EXPIRED | CANCELLED
```

Totales `quantity_committed` / `quantity_received` se **recalculan** desde aportes activos (`recomputeSupplyRequestStatus`).

## Reglas clave

- Cantidad pedida y comprometida **> 0**; `unitText` obligatorio.
- No over-pledge: suma comprometida ≤ cantidad pedida.
- Recepción parcial/total; aporte no cancelable tras `quantity_received > 0`.
- `internal_notes` / `internal_receipt_notes` **no** en listados públicos.
- Contenido monetario rechazado en títulos, descripciones y notas.
- Voluntarios M11 **no** reciben permisos M03 automáticos.
- Evidencia: solo prefijos M05; URLs públicas Supabase rechazadas (`FosterSecureRefValidator`).

## Repositorios (Android)

| Interfaz | Mock | Remoto |
|----------|------|--------|
| `ShelterCampaignRepository` | `MockShelterCampaignRepository` | Supabase (pendiente wiring producción) |
| `ShelterSupplyRepository` | `MockShelterSupplyRepository` | Supabase (pendiente) |

Store compartido: `M11ShelterMemoryStore` (`campaigns`, `campaignUpdates`, `supplyRequests`, `supplyContributions`, `auditEvents`, `m06Hooks`).

## Migración 043

Archivo: `supabase/migrations/043_m11_shelter_campaigns_and_aid.sql`

Tablas:

- `shelter_campaigns`
- `shelter_campaign_updates`
- `shelter_supply_requests`
- `shelter_supply_contributions`

RLS: `enable row level security`; `drop policy if exists` + políticas select; writes directos denegados (`with check (false)`).

RPC `m11_*`: CRUD campañas, updates, pedidos, pledge/cancel/confirm/receipt de aportes, listados públicos y por refugio.

**Forward-only** sobre 001–042. No modifica tablas M10/M11 bloque 1. **LOCAL ONLY** hasta apply remoto autorizado.

## M05 / M06 / M07

- **M05**: constraints SQL `^(m05://|file_asset:)` + validación Android.
- **M06**: `M11ShelterNotificationHooks` registra nombres de evento; migración explícita: no `m06_emit_domain_notification`.
- **M07**: `M11ShelterAuditEvents` + inserts catálogo observabilidad en 043.

## Pantallas

Listado público de campañas/pedidos, gestión por refugio, detalle y formulario de campaña, novedades, pedidos de insumos, aporte y recepción de contribuciones. Dashboard M11 enlaza **Campañas** y **Pedidos de insumos**.

ViewModels: `ShelterCampaignsAndAidViewModels.kt`. Rutas en `NavRoutes` / `ComunidappNavGraph`.

## Tests

| Archivo | Alcance |
|---------|---------|
| `M11ShelterCampaignsAndAidTest` | ~40 escenarios dominio mock + ViewModels |
| `M11CampaignMigrationStaticGuardsTest` | Contratos SQL 043 |

Compilar: `./gradlew :app:compileLocalDebugKotlin`.
Unit tests: `./gradlew :app:testLocalDebugUnitTest --tests "com.comunidapp.app.viewmodel.M11Shelter*"`

## Limitaciones

- Sin dinero, reputación, chat, push real.
- Migraciones 043–044 aplicadas en pruebas; 043 inmutable; hardening 044 PASS.
- Smoke UI manual de campañas/insumos: **PENDIENTE_EXTERNO** (no inventar PASS).
- Módulo M11: ver cierre `docs/03-modulos/M11-cierre-final.md`.

## Operación

Ver `docs/05-operacion/M11-aplicacion-migracion-043-supabase.md`.
