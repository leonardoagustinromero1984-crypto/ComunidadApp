# M17 — Cierre global: validación

**LeoVer / ComunidadApp** · **2026-08-02**  
**HEAD base:** `d74296a62a1c737f5183e4ff671cfbb9e3a65137`  
**Estado:** **CIERRE OFICIAL COMPLETADO**

---

## Entorno staging

| Campo | Valor |
|-------|-------|
| Entorno | Staging — **NO producción** |
| Project ref | `wyst****mizz` |
| Mecanismo | `supabase db query --linked` |
| Aplicación 054/055 | Manual por operador (Dashboard) |
| Correcciones | 056 moderador, 057 listado voluntariado |

---

## schema_migrations

Tras validación y reconciliación:

| Versión | Nombre | Registrada |
|---------|--------|------------|
| 054 | `054_m17_donation_campaigns_and_contributions` | Sí |
| 055 | `055_m17_in_kind_volunteering_and_transparency` | Sí |
| 056 | `056_m17_fix_moderator_helper` | Sí |
| 057 | `057_m17_fix_volunteer_public_list` | Sí |

**Nota:** 054/055 aplicadas manualmente antes del registro en `schema_migrations` (patrón M16). Reconciliación vía script de validación — **no** re-ejecución de migraciones completas.

---

## Objetos verificados

- **10 tablas** `m17_*` con RLS habilitado
- RPCs públicas y autenticadas M17
- Permisos M03 `donation.view` / `donation.manage`
- Helpers `_m17_*` con `search_path` seguro

---

## Validación SQL 01–120

Script: `scripts/ops/m17_remote_validation_054_055.sql`

**Resultado:** **120/120 PASS** (2026-08-02)

Correcciones aplicadas durante validación:

1. **056** — `_m17_is_moderator` usaba tabla inexistente `platform_role_assignments` en repo 054 original; staging recibió fix manual + migración 056.
2. **057** — `m17_list_public_volunteer_opportunities` referenciaba alias `o` fuera de scope (`v_row.id` correcto).
3. Ajustes menores en script de validación (expectativa financiera PRIVATE+CONFIRMED, `SET ROLE anon`, flujo postulación duplicada).

---

## Smoke remoto repositorios (estructural)

- `DataProvider` ramifica mock vs Supabase para campañas, bienes, voluntariado, transparencia
- Tipos `SupabaseM17*` presentes; mock preservado
- `registerMockContribution` remoto → `M17_PAYMENT_INFRASTRUCTURE_UNAVAILABLE`
- Administración extendida in-kind/voluntariado org vía RPC limitada (observe vacío en Supabase); mock completo para UI admin Bloque 3

---

## Tests focalizados

```
M17RemoteClosureGuardsTest
M17ExtendedRemoteMapperTest
M17ExtendedFoundationTest
M17DonationRemoteMapperTest
```

---

## Compilación

Reutilizada compilación PASS de `d74296a` + guards test (sin cambios Kotlin productivos M17 en cierre).

---

## M06 / M24

- M06 allowlist **no ampliada**; hooks diferidos documentados
- Pagos reales **M24**; CONFIRMED bloqueado en cliente

---

## Veredicto

```text
M17 MIGRACIONES 054 Y 055 APLICADAS EN STAGING
M17 VALIDACIÓN SQL/RLS/PRIVACIDAD 120/120 PASS
M17 CIERRE OFICIAL COMPLETADO
PAGOS REALES DIFERIDOS A M24
PRODUCCIÓN NO AFECTADA
```
