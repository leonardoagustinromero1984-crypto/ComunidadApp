# M15 — Persistencia remota

## Fuente autoritativa

```text
M10 — migraciones 040/041
Tablas: foster_home_profiles, foster_care_requests, foster_placements
RPC: m10_* (040 core + 041 gestión parcial)
```

M15 **no** crea tablas paralelas. Los repositorios `SupabaseM15Foster*` delegan en `Foster*Repository` existentes.

## Switching DataProvider

| Modo | Repositorios M15 |
|------|------------------|
| `useSupabase = true` | `SupabaseM15Foster*` + `SupabaseM15Placement*` → `Foster*Repository` → RPC M10 |
| `useSupabase = false` | `MockM15Foster*` + `MockM15Placement*` → `M15MemoryStore` |

Un solo store activo por modo. Sin caché persistente duplicada.

## Migración 053

```text
NO CREADA — Caso A
Highest migration permanece en 052
Guard CI m07: sin cambios
```

## Privacidad remota

RLS y RPC M10 ya restringen escrituras a `auth.uid()`. Proyección pública vía listados M10 excluye `private_address_text`.

## Bloque 4 — operaciones

| Modo | Repo |
|------|------|
| `useSupabase = true` | `SupabaseM15OperationsRepository` → `M15_REMOTE_VALIDATION_PENDING` |
| `useSupabase = false` | `MockM15OperationsRepository` → agregación sobre `M15MemoryStore` |

## Pendientes

- Apply remoto 040/041 ya validado en track M10 (independiente de M15 B2 local).
- Smoke funcional M15 con Supabase real: **PENDIENTE EXTERNO**.
- Métricas remotas: **PENDIENTE** (sin SQL nuevo en Bloque 4).

## Errores de infraestructura

```text
M15_INFRASTRUCTURE_UNAVAILABLE — red / serialización
M15_REMOTE_VALIDATION_PENDING — reservado para validaciones post-migración futuras
```

M10 `FOSTER_*` se normalizan a `M15_FOSTER_*` en cliente.
