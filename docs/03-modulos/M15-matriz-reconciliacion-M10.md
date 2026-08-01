# M15 — Matriz de reconciliación M10

## Decisión global

```text
CASO A — M10 cubre M15 Bloque 1 + persistencia base
SIN MIGRACIÓN 053
M10 ES LA PERSISTENCIA AUTORITATIVA DE M15
```

## Mapeo por concepto

| M15 | M10 persistente | Tabla / RPC | Decisión |
|-----|-----------------|-------------|----------|
| `M15FosterHome` | `FosterHomeProfile` | `foster_home_profiles` | REUTILIZAR |
| Disponibilidad derivada | `availability_status` + ocupación/reservas | columnas + RPC | REUTILIZAR |
| `M15FosterRequest` | `FosterHomeRequest` | `foster_care_requests` | REUTILIZAR |
| Reserva (ACCEPTED) | placement `RESERVED` + `reserved_count` | `foster_placements` + RPC | REUTILIZAR |
| Ingreso (ACTIVE) | `m10_start_foster_placement` | `foster_placements` | REUTILIZAR |
| `M15FosterPlacement` | `FosterPlacement` | `foster_placements` | REUTILIZAR |
| Autoridad | `auth.uid()` + RLS 040 | RPC `m10_*` | REUTILIZAR |
| Auditoría | eventos locales M15 | M07 best-effort | ADAPTAR |
| Notificaciones | `M15M06Hooks` | sin push real | PREPARADO |

## RPC M10 reutilizadas (040/041)

| Operación M15 | RPC M10 |
|---------------|---------|
| Listar disponibles | `m10_list_available_foster_homes` |
| Mi hogar | `m10_get_my_foster_home` |
| Detalle hogar | `m10_get_foster_home` |
| Crear / actualizar | `m10_create_foster_home`, `m10_update_foster_home` |
| Activar | `m10_set_foster_home_status` |
| Enviar solicitud | `m10_submit_foster_request` |
| Revisar / aceptar / rechazar | `m10_mark_*`, `m10_accept_*`, `m10_reject_*` |
| Iniciar ingreso | `m10_start_foster_placement` |
| Listar placements activos | `m10_list_active_foster_placements` |
| Evolución | `m10_add_foster_evolution` + entries 041 |
| Egreso | `m10_complete_foster_placement` |
| Gastos | foster_expenses RPC 041 |
| Ayuda | foster_help_requests RPC 041 |

## Estados

| M15 | M10 / SQL |
|-----|-----------|
| DRAFT, ACTIVE, PAUSED, SUSPENDED, CLOSED | idénticos en `foster_home_profiles.status` |
| AVAILABLE, LIMITED, FULL, UNAVAILABLE | idénticos en `availability_status` |
| SUBMITTED … EXPIRED | idénticos en `foster_care_requests.status` |
| RESERVED, ACTIVE, COMPLETED, CANCELLED | idénticos en `foster_placements.status` |

## Diferencias semánticas

| Tema | Resolución |
|------|------------|
| Prefijo código error | M15 mapea `FOSTER_*` → `M15_FOSTER_*` vía `M15ErrorMapper.fromM10Code` |
| Store local B1 | `M15MemoryStore` solo cuando `useSupabase = false` |
| Legacy `foster_homes` (006) | INCOMPATIBLE — no usado por M10 ni M15 |
| Gastos / evolución / ayuda | M10 041 — **M15 B3 adaptadores SupabaseM15Placement*** |

## Datos que NO se duplican

- Perfiles de hogar
- Solicitudes de tránsito
- Placements / reservas / ocupación
- Capacidad y contadores

## Brechas detectadas

Ninguna brecha que requiera 053. Columnas y RPC M10 cubren el alcance M15 Bloque 1–3.

## Implementación cliente

| Capa | Archivo |
|------|---------|
| Mappers | `M15FosterMappers.kt` |
| Adaptadores Supabase | `SupabaseM15FosterRepositories.kt`, `SupabaseM15LifecycleRepositories.kt` |
| Mappers lifecycle | `M15LifecycleMappers.kt` |
| Delegación M10 | `SupabaseFosterRepositories.kt` + `SupabaseFosterM10RemoteDataSource.kt` |
| Switching | `DataProvider` (`useSupabase`) |
