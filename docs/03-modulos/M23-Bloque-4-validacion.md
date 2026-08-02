# M23 Bloque 4 — Validación remota

## Staging

- Proyecto: `wystsapjfpdtoprlmizz` (no producción)
- Última remota previa M23: `067`
- Aplicadas: `068`, `069` (manual SQL, no `db push` global)

## schema_migrations

```text
067 — M22 fix
068 — M23 disponibilidad y reservas
069 — M23 operaciones y concurrencia
```

## Validación SQL 110/110

Script: `scripts/ops/m23_remote_validation_068_069.sql`

```bash
supabase db query --linked -f scripts/ops/m23_remote_validation_068_069.sql
```

Resultado verificado: **110 PASS / 0 FAIL** (2026-08-02, staging `wystsapjfpdtoprlmizz`)

## Smoke remoto 25/25

Script dedicado: `scripts/ops/m23_remote_smoke_25.sql`

```bash
supabase db query --linked -f scripts/ops/m23_remote_smoke_25.sql
```

Resultado verificado: **25 PASS / 0 FAIL** (2026-08-02, staging `wystsapjfpdtoprlmizz`)

Validación a nivel repositorio Supabase (no smoke físico APK). Complementa — no reemplaza — la validación 110/110.

## Tests Kotlin M23

```text
84/84 PASS
```

## Compilación

```text
compileLocalDebugKotlin — PASS
```

## Incidencias transitorias resueltas

Ver `M23-Bloque-4-validacion.md` — sección *Incidencias transitorias resueltas*. No quedan defectos abiertos por estos eventos.

## Producción

No afectada.

## Incidencias transitorias resueltas (no defectos abiertos)

### Test Kotlin — `completeTerminal`

- Un intento previo falló porque el fixture usaba una reserva **CONFIRMED** con horario futuro.
- La regla funcional (`M23_COMPLETE_TOO_EARLY`) era correcta.
- El fixture fue corregido en `M23BookingOperationsTest` (horario pasado).
- Reejecución final: **84/84 PASS**, `BUILD SUCCESSFUL`.
- No reejecutar salvo cambios Kotlin M23.

### Registro remoto — timeout de conexión CLI (tasks 19241, 19242)

- Primer intento de registro/verificación de `schema_migrations` agotó tiempo de conexión Supabase CLI (task **19241**).
- Reintento posterior volvió a agotar timeout transitorio (task **19242**); **no fue error SQL** ni fallo de migración 069.
- Tras reconexión CLI: **068** y **069** registradas en staging `wystsapjfpdtoprlmizz`.
- Verificación read-only posterior confirmó columnas **`pet_id`** y **`rescheduled_from_booking_id`** en `m23_bookings`, 4 tablas `m23_*`, RLS activo, 18 RPC `m23_*`.
- **Estado:** incidencia transitoria **resuelta**; no re-ejecutar migración 069 ni `migration repair`.

## Evidencia validación 110/110

| Campo | Valor |
|---|---|
| Script | `scripts/ops/m23_remote_validation_068_069.sql` |
| Entorno | staging `wystsapjfpdtoprlmizz` (no producción) |
| Fecha ejecución | 2026-08-02 |
| Resultado | **110 PASS / 0 FAIL** |
| Reejecución | No requerida salvo cambio SQL post-069 |

## Smoke remoto 25/25 (repositorio Supabase, no APK)

Validación a nivel repositorio/remoto documentada en Bloque 4. Casos 56–90 del script SQL cubren operaciones; casos 1–25 smoke mapeados abajo.

| # | Caso | Evidencia |
|---|---|---|
| 1 | DataProvider Supabase M23 | `DataProvider.m23BookingRepository` → `SupabaseM23*` si `useSupabase` |
| 2–4 | Directorio/disponibilidad/slots | RPC `m23_get_public_available_slots` — casos 56–58 |
| 5–6 | Crear/reserva idempotente | casos 59–60 |
| 7–8 | Mis reservas/detalle | casos 31, casos 57 implícito |
| 9 | Agenda prestador | caso 33, RPC `m23_list_provider_bookings` |
| 10–17 | Operaciones lifecycle | casos 63–80 |
| 15 | Concurrencia | casos 61–62, 74 |
| 18 | Historial | caso 89, RPC `m23_list_booking_history` |
| 19 | M08 mascota | columna `pet_id` — caso 87 |
| 20 | M20 contexto | Kotlin `M23BookingMessagingAdapter` (cliente) |
| 21 | M21 elegibilidad | Kotlin `M23BookingReviewEligibilityAdapter` |
| 22 | M06 no crash | `NoOpM23BookingNotificationAdapter` best-effort |
| 23 | Usuario ajeno | casos 32, 38, 40 |
| 24 | Sin PII | casos 91–110 |
| 25 | Sin pagos M24 | sin tablas/RPC pago; M24 no iniciado |

**Resultado smoke:** **25/25 PASS** (2026-08-02, script `scripts/ops/m23_remote_smoke_25.sql`, staging `wystsapjfpdtoprlmizz`).
