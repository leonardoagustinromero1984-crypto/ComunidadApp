# M13 Bloque 2 — Validación local

```text
M13 BLOQUE 2 CERRADO LOCALMENTE
MIGRACIÓN 048 PENDIENTE DE APLICACIÓN REMOTA
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
M12 CIERRE OFICIAL PENDIENTE
```

## Checklist

| # | Criterio | Resultado |
|---|----------|-----------|
| 1 | Migración `048_m13_sightings_and_match_candidates.sql` creada | PASS |
| 2 | Migraciones 001–047 intactas | PASS |
| 3 | Sin migración 049 | PASS |
| 4 | Tabla lateral + candidatos + decisiones + historial | PASS |
| 5 | Legacy `lost_found_sightings` preservado | PASS |
| 6 | 13 RPC cliente; sin confirm/reject | PASS |
| 7 | RLS + grants authenticated; helpers revocados | PASS |
| 8 | Repos Supabase + DataProvider switching | PASS |
| 9 | Guard CI highest = 048 | PASS |
| 10 | Sin apply remoto / sin APK | PASS |
| 11 | M12 no declarado cerrado | PASS |

## Validaciones locales

- `bash -n scripts/ci/m07_quality_checks.sh`
- `bash scripts/ci/m07_quality_checks.sh` → highest **048**
- Suites focalizadas M13 (+ regresiones highest-migration)
- `.\gradlew.bat compileLocalDebugKotlin`

## Fuera de alcance (Bloque 3)

Confirmación/rechazo remoto final, RPC de decisión, UI de cierre remoto de match, migración 049.
