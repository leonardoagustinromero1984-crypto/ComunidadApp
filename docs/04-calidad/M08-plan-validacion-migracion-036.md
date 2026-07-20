# M08 â€” Plan de validaciÃ³n migraciÃ³n 036

**Producto:** LeoVer
**Alcance:** local only (`supabase db reset`, matrices 035+036, lint).
**Staging:** NO autorizado.

## Prechecks

1. Docker / `supabase status` local OK
2. Highest migration esperado 036 tras reset
3. 037 ausente
4. 001â€“035 intactas en git

## Matriz SQL

Script: `scripts/sql/m08_validate_local_036.sql`

Estados: PASS | FAIL | NOT_EXECUTED | BACKLOG (justificado).

Cobertura mÃ­nima: historial 001â€“036; 4 RPCs; grants; search_path; DEFINER; profile/health/context/list; microchip; org principal; CRUD directo revocado; helpers internos; ausencia vitrina pÃºblica; runner_summary FAIL=0.

## Doble reset

1. `supabase db reset` â†’ list â†’ lint â†’ matriz 035 â†’ matriz 036
2. Repetir desde cero

## RegresiÃ³n Android (sin cambios de cÃ³digo)

- `:app:testDebugUnitTest`
- `:app:assembleDebug`
- `:app:lintDebug`
- `:app:jacocoTestReport`

## Quality gates

- `m07_quality_checks.sh`
- `m08_stage2_quality_checks.sh`
- `m08_stage3_freeze_quality_checks.sh`
- `m08_stage3b_quality_checks.sh`
- `m08_stage3c_quality_checks.sh`

## Criterio de cierre

```text
M08 ETAPA 3C â€” FORWARD-FIX 036 VALIDADO LOCALMENTE
STAGING NO AUTORIZADO
REQUIERE ETAPA 4B â€” REPOSITORIOS Y ADAPTADOR LEGACY
```
