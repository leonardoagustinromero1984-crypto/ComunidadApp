# BASE PRE-M08 INTEGRADA

**Fecha:** 2026-07-19  
**Producto:** LeoVer  
**Rama:** `integration/pre-m08-staging-pass`  
**Commit de integración (base M07):** `288b437`  
**Checkpoint:** `checkpoint/m07-staging-pass-001-034` → `288b437`  
**Commit final M07:** `288b437` — `test(m07): close staging validation for migrations 001-034`  
**Base `origin/main`:** `98d287c`

---

## 1. Ancestría y fast-forward

| Chequeo | Resultado |
|---|---|
| `origin/main` es ancestro de `288b437` | **SÍ** (`merge-base --is-ancestor` exit 0) |
| `merge-base(origin/main, 288b437)` | `98d287c` (= tip de `origin/main`) |
| Integración | `git merge --ff-only 288b437` desde `origin/main` → **PASS** |
| HEAD integración | `288b437` (historia completa M07 incluida) |
| `main` modificado | **NO** |

---

## 2. Módulos presentes en la historia integrada

| Módulo | Evidencia en historia / docs | Clasificación para M08 |
|---|---|---|
| M02 — Usuarios, roles y permisos | Cierre `docs/02-arquitectura/M02-cierre-final.md`; migraciones 015–018; `has_permission`; reportes Etapa 5 | **DISPONIBLE** |
| M03 — Organizaciones y equipos | Cierre `M03-cierre-final.md`; migraciones 019–021; `organization_memberships`; invitaciones/sucursales | **DISPONIBLE** |
| M05 — Archivos, media y documentos | Cierre `M05-cierre-final.md`; migraciones 024–025; storage RLS/RPC; contratos `FileAsset*` | **DISPONIBLE** |
| M07 — Auditoría / observabilidad | `M07 — CERRADO EN STAGING`; migraciones 029–034; catálogos 118/28/14; 8 permisos | **DISPONIBLE** |

Nota: los reportes históricos M02/M03/M05 pueden aún decir “PENDIENTE DE VALIDACIÓN REMOTA” a nivel checklist de módulo. Eso **no** contradice la validación acumulada staging **001–034 PASS** (matriz SQL + lint + apply), que cubre el esquema y contratos remotos requeridos. Deuda documental aislada — no bloquea arranque de M08.

Componentes transversales observados (sin implementar cambios): `PetRepository` / pets (legado producto), `audit_events`, `has_permission`, paths de storage — presentes en el árbol.

---

## 3. Migraciones

| Campo | Valor |
|---|---|
| Archivos locales | **001–034** (34) |
| Remoto staging (evidencia M07) | alineado 001–034 |
| Migración 035 | **NO** creada |

---

## 4. Calidad Android (rama de integración)

| Chequeo | Resultado |
|---|---|
| `testDebugUnitTest` | **559** · 0 failures · exit 0 |
| `assembleDebug` | **PASS** |
| `lintDebug` | **PASS** (`LINT_EXIT=0`) |
| `jacocoTestReport` | **PASS** (`JACOCO_EXIT=0`) |
| `scripts/ci/m07_quality_checks.sh` | **PASS** · highest **034** · 118 / 28 / 14 · permisos OK |

---

## 5. Supabase staging (evidencia heredada M07)

```text
VALIDACIÓN STAGING PASS
M07 CERRADO EN STAGING
```

- Matriz SQL 001–034: 259 PASS físicos · 0 FAIL  
- DB lint remoto: 0 errores  
- OTP / username / smoke APK: PASS  
- Producción: no tocada / no validada  

---

## 6. Diferenciación de release

```text
M07 RELEASE TÉCNICO: HABILITADO
VALIDACIÓN STAGING: PASS
RELEASE PRODUCTIVO GLOBAL: BLOQUEADO
```

Motivos del bloqueo **global** (no son fallo de M07):

1. EXPORTACIÓN DE ARCHIVO PENDIENTE  
2. INTEGRACIÓN M06 PENDIENTE (envío)  
3. Producción aún no preparada  

---

## 7. Deudas y riesgos

| Ítem | Tipo |
|---|---|
| Exportación de archivo real | deuda producto |
| Envío M06 completo | deuda producto |
| Warnings db lint no bloqueantes | backlog |
| Reportes M02/M03/M05 “remoto pendiente” antiguos | deuda documental aislada |
| Secretos en tree | **no** hallados como tracks ilegítimos (coincidencias textuales legítimas: UI password, ADR, `device_tokens`, `backup_rules.xml`, `local.properties.example`) |

---

## 8. Prohibiciones vigentes

- No modificar `main` directamente / no merge automático desde esta tarea.  
- No force push / no reescribir historia.  
- No `db push` / reset remoto / repair / SQL remoto desde Cursor.  
- No migración 035.  
- No iniciar M08 hasta checklist de inicio explícito.  

---

## 9. Criterio para iniciar M08

M08 puede iniciarse cuando:

1. Esta base permanece en `288b437`+doc o sucesor ff-only equivalente.  
2. Dependencias críticas M02/M03/M05/M07 siguen **DISPONIBLES**.  
3. Calidad local verde en la rama de trabajo de M08.  
4. Existe instrucción explícita de inicio de M08 (no automática).  

---

## Estado final

```text
BASE INTEGRADA PRE-M08 — LISTA
```

**Siguiente acción exacta:** abrir Pull Request de `integration/pre-m08-staging-pass` → `main` (revisión humana); **no** iniciar M08 ni mergear sin aprobación.
