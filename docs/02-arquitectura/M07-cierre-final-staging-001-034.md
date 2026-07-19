# M07 — Cierre final staging 001–034

**Fecha:** 2026-07-19  
**Producto:** LeoVer  
**Rama:** `m07/cierre-final-staging-001-034`  
**Commit base 034:** `e4574fa`  
**Entorno:** staging / pruebas (no producción)

```text
M07 — CERRADO EN STAGING
VALIDACIÓN STAGING PASS
```

Producción **no** lista. Release productivo **no** autorizado.

---

## 1. Alcance

M07 entrega el foundation de auditoría, analítica y observabilidad operativa: catálogos deny-by-default, writers `SECURITY DEFINER`, correlación, sanitización, permisos dedicados, métricas, health, alertas/incidentes, retención/legal hold y exportaciones con archivo pendiente honesto.

Android consume vía RPC/mocks; no escribe tablas remotas arbitrarias; no usa service role; no autodeclara staff.

---

## 2. Etapas ejecutadas

| Etapa | Contenido |
|---|---|
| 1–5 | Persistencia, métricas/health/alertas, retención/permisos, hardening operativo |
| 6 | Validación final / gates 032 |
| Staging lint | Migración **033** (L01–L11 runtime/lint) |
| Grants internos | Migración **034** (REVOKE anon/authenticated en helper invitaciones) |
| Cierre | Matriz SQL 001–034 PASS + smoke Auth/OTP/APK |

---

## 3. Migraciones 029–034

| Migración | Rol |
|---|---|
| 029 | Fundación auditoría / security / application errors |
| 030 | Métricas, health, alertas/incidentes |
| 031 | Retención, permisos, instrumentación |
| 032 | Hardening Stage 6 |
| 033 | Fixes lint/runtime staging (L01–L11) |
| 034 | Grants `_resolve_invitation_by_token` (sin body/mode/search_path) |

Historial staging: **001–034** alineado · 34 versiones · max 034 · 0 faltantes · 0 duplicadas.

---

## 4. Defectos cerrados en staging

- **033:** L01–L11 (lint/runtime) aplicados y lint remoto PASS.
- **034:** grant residual `anon` EXECUTE sobre helper DEFINER interno — **DEFECTO DE GRANTS CERRADO**.
- Matriz: dos falsos positivos sobre `org_hash_invitation_token` (INVOKER + search_path unset) corregidos en script 001–034.

---

## 5. Validación

| Capa | Resultado |
|---|---|
| Local 001–034 (2× reset + lint) | PASS |
| Apply remoto 034 | PASS |
| `db lint` remoto | PASS (0 errores; warnings backlog) |
| Matriz SQL staging 001–034 | **PASS** (0 FAIL) |
| RLS / grants / catálogos | PASS (118 / 28 / 14 / 8) |
| Auditoría / errores / métricas / health / incidentes / retención | estructurales PASS |
| Exportación | contrato seguro PASS; **archivo pendiente** |
| Integración M06 | event key catalogado; **envío pendiente** |
| Smoke APK + OTP 8 + username | PASS |
| Android 559 / assemble / lint | PASS |

---

## 6. Riesgos residuales y deuda

| Ítem | Tipo |
|---|---|
| EXPORTACIÓN DE ARCHIVO PENDIENTE | deuda producto |
| INTEGRACIÓN M06 (envío) PENDIENTE | deuda producto |
| Warnings db lint no bloqueantes | backlog |
| `internal_body_prohibition_e2e` | backlog |
| `observability_loop_absence_runtime` | backlog |
| Probes runtime JWT en SQL Editor | NOT_EXECUTED justificados |

---

## 7. Criterios de reapertura de M07

Reabrir M07 solo si aparece:

- regresión de grants en writers internos;
- drift de catálogos 118/28/14/8;
- FAIL nuevo en matriz SQL staging;
- error de `db lint` (no warning);
- rotura Auth/OTP/username que dependa de contratos M07;
- necesidad de migración **035+** de observabilidad.

---

## 8. Handoff módulos futuros

- Consumir permisos/event keys ya catalogados; no inventar writers públicos.
- Exportación archivo real e integración M06 de envío: fuera del cierre M07 staging.
- **M08** no iniciado en este cierre.
- `main` / producción no modificados.

---

## 9. Estado final

```text
M07 — CERRADO EN STAGING
VALIDACIÓN STAGING PASS
RELEASE BLOQUEADO
```

`RELEASE BLOQUEADO` = decisión de producto/deudas restantes, no fallo de validación staging M07.
