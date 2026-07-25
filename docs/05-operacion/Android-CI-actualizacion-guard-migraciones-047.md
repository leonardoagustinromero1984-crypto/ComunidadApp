# Android CI — actualización del guard de migraciones (→ 049)

## Actualización deliberada M13 revisión remota → 049

Al crear `049_m13_match_review_workflow.sql`, el guard se actualizó **deliberadamente**:

| Campo | Antes | Ahora |
|-------|-------|-------|
| Título | `Migration numbering 001–048` | `Migration numbering 001–049` |
| Condición | highest == `048` | highest == `049` |
| Techo futuro | falla ante 049 sin update | falla ante **050** sin update |

No se usó rango permisivo ni `continue-on-error`. El workflow CI **no** se debilitó.

## Validaciones

```text
bash -n scripts/ci/m07_quality_checks.sh
bash scripts/ci/m07_quality_checks.sh
```

Resultado esperado: quality checks PASSED y `Highest migration: 049`.

## Confirmaciones

- Migraciones **001–048** intactas.
- Migración **049** creada (M13 review); **no aplicada remotamente**.
- Sin migración **050**.
- Smoke B2 M13: **PENDIENTE EXTERNO**.
- M12 smoke / cierre: **PENDIENTES**.
- M13 **no** cerrado oficialmente.

---

## Historial breve

- Hotfix a **047** (M12) → luego **048** (M13 B2) → ahora **049** (revisión humana remota).
- Otros hotfixes CI (`localDebug`, M09 IDs) se conservan.
