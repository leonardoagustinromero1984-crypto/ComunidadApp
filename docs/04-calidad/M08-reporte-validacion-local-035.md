# M08 — Reporte validación local migración 035

**Estado:** `M08 ETAPA 3B — MIGRACIÓN 035 VALIDADA LOCALMENTE`  
**STAGING NO AUTORIZADO** · **REQUIERE ETAPA 4**

## Entorno

- Supabase local (Docker), sin `--linked`, sin `db push` remoto.
- Resets completos: ≥2 (`supabase db reset`) — ambos aplicaron 001–035.
- Lint: `supabase db lint --local --level warning --fail-on error` → exit 0 (warnings históricos + `m08_set_pet_avatar_asset` unused var warning-extra).

## Historial local

| Check | Resultado |
|---|---|
| Migraciones | 001–035 (35) |
| Max | 035 |
| Dupes / missing | 0 |
| 036 | ausente |

## Matriz SQL (`scripts/sql/m08_validate_local_035.sql`)

Última ejecución post-reset:

| Métrica | Valor |
|---|---|
| PASS | 50 |
| FAIL | **0** |
| NOT_EXECUTED | 0 |
| BACKLOG | 1 (envío real M06 PET) |
| `runner_summary` | **PASS** |

Cobertura: DDL, backfill vacío local, XOR, principal único, soft-unique microchip, grants/PUBLIC, RLS, search_path DEFINER, M02×14, eventos M08×16, RPC create/transfer/archive/restore/deceased, bloqueo `owner_id` directo, authorized sin ownership, historial conservado.

## Quality gates

| Gate | Esperado |
|---|---|
| `m08_stage2_quality_checks.sh` | PASS |
| `m08_stage3_freeze_quality_checks.sh` | PASS (ajustado: max 034\|035) |
| `m08_stage3b_quality_checks.sh` | PASS |
| Android unit/assemble/lint/jacoco | ver commit / CI local Etapa 3B |

## Riesgos / deuda

1. **Staging block** hasta Etapa 4 (CRUD Android legacy roto si se aplica 035 remoto).
2. Emisión de eventos M07 en RPC = deuda (catálogo sí).
3. Notificaciones M06 reales = BACKLOG.
4. Seed local sin pets → backfill trivial (0=0); lógica aborta con null owners / chip dups si existieran.

## Conclusión

035 es ejecutable y determinística en local. **No autorizada** para staging/producción.
