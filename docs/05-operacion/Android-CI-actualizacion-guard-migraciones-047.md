# Android CI — actualización del guard de migraciones (→ 052)

## Actualización deliberada M14 Bloque 3 → 052

Al crear `052_m14_credential_verification_and_public_access.sql`, el guard se actualizó **deliberadamente**:

| Campo | Antes | Ahora |
|-------|-------|-------|
| Título | `Migration numbering 001–051` | `Migration numbering 001–052` |
| Condición | highest == `051` | highest == `052` |
| Techo futuro | falla ante 052 sin update | falla ante **053** sin update |

Archivo: `scripts/ci/m07_quality_checks.sh`

## Notas

- **050/051** aplicadas remotamente; **052** creada y no aplicada.
- Historial: 047 → 048 → 049 → 050 → 051 → ahora **052**.
- M12/M13 smokes y cierres oficiales siguen **PENDIENTES EXTERNOS**.
