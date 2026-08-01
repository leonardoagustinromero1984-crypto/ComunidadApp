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
| `useSupabase = true` | `SupabaseM15Foster*Repository` → `SupabaseFoster*Repository` → RPC M10 |
| `useSupabase = false` | `MockM15Foster*Repository` → `M15MemoryStore` |

Un solo store activo por modo. Sin caché persistente duplicada.

## Migración 053

```text
NO CREADA — Caso A
Highest migration permanece en 052
Guard CI m07: sin cambios
```

## Privacidad remota

RLS y RPC M10 ya restringen escrituras a `auth.uid()`. Proyección pública vía listados M10 excluye `private_address_text`.

## Pendientes

- Apply remoto 040/041 ya validado en track M10 (independiente de M15 B2 local).
- Smoke funcional M15 con Supabase real: **PENDIENTE MANUAL**.
- Enlace hub M15 desde Sumate: **PENDIENTE** (legacy `foster_*` activo).

## Errores de infraestructura

```text
M15_INFRASTRUCTURE_UNAVAILABLE — red / serialización
M15_REMOTE_VALIDATION_PENDING — reservado para validaciones post-migración futuras
```

M10 `FOSTER_*` se normalizan a `M15_FOSTER_*` en cliente.
