# M14 Bloque 4 — Validación local

```text
M14 BLOQUE 4 CERRADO LOCALMENTE
M14 CIERRE TÉCNICO LOCAL COMPLETADO
MIGRACIÓN 052 PENDIENTE DE APLICACIÓN REMOTA
VALIDACIÓN ESTRUCTURAL 052 PENDIENTE
M14 SMOKE FUNCIONAL PENDIENTE EXTERNO
M14 CIERRE OFICIAL PENDIENTE
GITHUB ANDROID CI PENDIENTE
M13 SMOKE FUNCIONAL PENDIENTE EXTERNO
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
```

## Contexto remoto previo

- 050/051 aplicadas; validación estructural **18/18 PASS**; `DML_DIRECTO_CLIENTE = 0`.
- Migración **052** creada en repo; **no aplicada remotamente**.
- GitHub Android CI fallido; **no corregido en este bloque**.

## Pruebas automáticas

**No ejecutadas** en este cierre por decisión del usuario. La validación funcional queda **manual** y diferida hasta apply remoto de 052.

Suites presentes (no corridas aquí):

- `M14Block4HardeningTest` — 11 casos (expiraciones, privacidad, métricas, M06, fallback remoto, errores).
- `M14Block4StaticGuardsTest` — 6 guards (migraciones 001–052, wiring, secretos, códigos error).

## Checklist local

| # | Criterio | Resultado |
|---|----------|-----------|
| 1 | Expiración `PENDING` por TTL | PASS_LOCAL |
| 2 | Expiración `UNDER_REVIEW` por TTL | PASS_LOCAL |
| 3 | Credencial vencida por `expiresAt` | PASS_LOCAL |
| 4 | Estados terminales preservados | PASS_LOCAL |
| 5 | Idempotencia / `ALREADY_APPLIED` | PASS_LOCAL |
| 6 | Privacidad pública / QR sin PII | PASS_LOCAL |
| 7 | Microchip enmascarado | PASS_LOCAL |
| 8 | Métricas agregadas sin PII | PASS_LOCAL |
| 9 | Rango métricas inválido tipificado | PASS_LOCAL |
| 10 | Hooks M06 preparados (sin push real) | PASS_LOCAL |
| 11 | M07 compatible (sin ampliar catálogo) | PASS_LOCAL |
| 12 | Fallback remoto `REMOTE_VALIDATION_PENDING` | PASS_LOCAL |
| 13 | Migraciones 001–052 intactas; sin 053 | PASS_LOCAL |
| 14 | `compileLocalDebugKotlin` | PASS (2026-08-01) |
| 15 | Apply remoto 052 | PENDIENTE_EXTERNO |
| 16 | Validación estructural 052 | PENDIENTE_EXTERNO |
| 17 | Smoke funcional M14 remoto | PENDIENTE_EXTERNO |
| 18 | GitHub Android CI | PENDIENTE_EXTERNO |
