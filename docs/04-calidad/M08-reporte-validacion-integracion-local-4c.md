# M08 — Reporte validación integración local 4C

**Producto:** LeoVer  
**Rama:** `m08/etapa-4c-integracion-local-smoke-apk`

```text
M08 ETAPA 4C — INTEGRACIÓN LOCAL AUTOMÁTICA PASS
SMOKE APK MANUAL — PENDIENTE
STAGING NO AUTORIZADO
```

## Resultados automáticos

| Check | Resultado |
|---|---|
| `supabase db reset` | PASS (001–036) |
| migration list max 036 | PASS |
| 037 ausente | PASS |
| lint local exit 0 | PASS |
| matriz 035 | PASS=50 FAIL=0 BACKLOG=1 |
| matriz 036 | PASS=49 FAIL=0 BACKLOG=1 |
| fixtures smoke SQL | PASS (schema ready) |
| Android unit tests | PASS (623 tests, 0 failures) |
| assembleDebug | PASS |
| lintDebug | PASS |
| JaCoCo | PASS (`:app:jacocoTestReport`) |
| quality M07→4C | PASS (m07, m08_stage2, m08_stage3_freeze, m08_stage3b, m08_stage3c, m08_stage4b, m08_stage4c) |
| APK generado | PASS (`apk/LeoVer-M08-Stage4C-debug.apk`, untracked) |

## Configuración

- `local.properties` gitignored; URL emulador `10.0.2.2` + puerto API local.
- Debug cleartext limitado; release cleartext OFF.
- Sin `service_role` en Android.

## Riesgos / deuda

- Smoke manual pendiente (checklist).
- Emisión runtime M07 `m08.pet.updated` (BACKLOG).
- M06 hooks reales (BACKLOG).
- Staging incompatible hasta apply coordinado 035+036.

## Confirmaciones

- 037: NO  
- Migraciones 001–036 modificadas: NO  
- Supabase remoto: NO  
- Staging autorizado: NO  
- SMOKE PASS declarado: **NO** (pendiente humano)
