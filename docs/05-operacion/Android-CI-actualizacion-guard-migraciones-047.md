# Android CI — actualización del guard de migraciones (→ 051)

## Actualización deliberada M14 Hotfix 051

Al crear `051_m14_revoke_residual_table_privileges.sql`, el guard se actualizó **deliberadamente**:

| Campo | Antes | Ahora |
|-------|-------|-------|
| Título | `Migration numbering 001–050` | `Migration numbering 001–051` |
| Condición | highest == `050` | highest == `051` |
| Techo futuro | falla ante 051 sin update | falla ante **052** sin update |

Archivo: `scripts/ci/m07_quality_checks.sh`

Resultado esperado: quality checks PASSED y `Highest migration: 051`.

## Notas

- Migración **051** creada (hotfix privilegios residuales post-050); **no aplicada remotamente**.
- **050** aplicada remotamente; permanece intacta.
- Historial: **047** → **048** → **049** → **050** → ahora **051**.
- M12/M13 smokes y cierres oficiales siguen **PENDIENTES EXTERNOS**.
