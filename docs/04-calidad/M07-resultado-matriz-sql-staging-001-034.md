# M07 — Resultado matriz SQL staging 001–034

**Fecha:** 2026-07-19  
**Producto:** LeoVer  
**Entorno:** staging / pruebas (ref …`mizz`)  
**Commit migración 034:** `e4574fa` — `fix(m07): restrict internal invitation helper execution`  
**Script:** `scripts/sql/m07_validate_staging_001_034.sql`  
**Ejecución:** Supabase SQL Editor (manual; sin reejecución desde Cursor)

---

## Historial

| Campo | Valor |
|---|---|
| Versiones | **34** |
| Máxima | **034** |
| Local/Remote | alineados 001–034 |
| Faltantes | 0 |
| Duplicadas | 0 |

---

## Conteos

### CSV físico (export SQL Editor)

| Métrica | Valor |
|---|---|
| Filas físicas | **267** |
| PASS | **259** |
| FAIL | **0** |
| NOT_EXECUTED | **3** |
| BACKLOG | **5** |

### `runner_summary` interno

| Métrica | Valor |
|---|---|
| total | 266 |
| pass | 258 |
| fail | 0 |
| not_executed | 3 |
| backlog | 5 |
| status | **PASS** |

### Diferencia de una fila

`runner_summary` calcula los totales **antes** de insertarse a sí mismo. El CSV físico incluye luego esa fila adicional con status PASS. No hay FAIL oculto ni discrepancia de contrato.

---

## Checks obligatorios

| Área | Resultado |
|---|---|
| Historial 001–034 | **PASS** |
| 118 event keys | **PASS** |
| 28 metric keys | **PASS** |
| 14 health checks | **PASS** |
| 8 permisos M07 | **PASS** |
| Duplicados de catálogos | **0** |
| RLS | **PASS** |
| Grants | **PASS** |
| SECURITY DEFINER / search_path (inventario correcto) | **PASS** |
| Gates funcionales | **PASS** |
| Hardening 032 | **PASS** |
| Correcciones 033 | **PASS** |
| Corrección grants 034 | **PASS** |
| `internal_writers_anon_execute` | **0** |
| `internal_writers_public_execute` | **0** |
| Incidentes estructurales | **PASS** |
| Retención estructural | **PASS** |
| Exportación segura | **PASS** (deuda filePending) |
| Integración M06 | catalogada; sin envío simulado |

### Grants `_resolve_invitation_by_token`

| Flag | Valor |
|---|---|
| PUBLIC EXECUTE | false |
| anon directo | false |
| anon efectivo | false |
| authenticated | false |
| service_role | true |

### `org_hash_invitation_token`

| Contrato | Valor |
|---|---|
| Security mode | SECURITY **INVOKER** |
| Digest | `extensions.digest` calificado |
| Forma | helper puro (sin table access) |
| Exigencia falsa DEFINER | **eliminada** de la matriz |
| Exigencia falsa search_path | **eliminada** de la matriz |

---

## NOT_EXECUTED justificados

1. `kotlin_catalog_equality` — no evaluable solo desde SQL Editor; cubierto por suite Android / quality.
2. `runtime_transition_probe` — requiere JWT staff; `auth.uid()` null; sin incidentes persistentes.
3. `runtime_preview_execute_probe` — requiere JWT staff; sin mutación ni fixture inventado.

## BACKLOG documentado

1. `internal_body_prohibition_e2e`
2. `observability_loop_absence_runtime`
3. EXPORTACIÓN DE ARCHIVO PENDIENTE
4. INTEGRACIÓN M06 PENDIENTE
5. Warnings remotos de db lint (no bloqueantes)

---

## Conclusión

```text
MATRIZ SQL STAGING 001–034 PASS
```

Producción no validada. Release productivo no autorizado por este resultado.
