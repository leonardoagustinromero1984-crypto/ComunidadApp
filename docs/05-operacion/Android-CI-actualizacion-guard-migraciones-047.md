# Android CI — actualización del guard de migraciones (→ 050)

## Actualización deliberada M14 Bloque 2 → 050

Al crear `050_m14_pet_passports_and_credentials.sql`, el guard se actualizó **deliberadamente**:

| Campo | Antes | Ahora |
|-------|-------|-------|
| Título | `Migration numbering 001–049` | `Migration numbering 001–050` |
| Condición | highest == `049` | highest == `050` |
| Techo futuro | falla ante 050 sin update | falla ante **051** sin update |

Archivo: `scripts/ci/m07_quality_checks.sh`

Resultado esperado: quality checks PASSED y `Highest migration: 050`.

## Notas

- Migración **050** creada (M14 pasaportes); **no aplicada remotamente**.
- Hotfix histórico: **047** → **048** → **049** → ahora **050**.
- M12/M13 smokes y cierres oficiales siguen **PENDIENTES EXTERNOS**.
