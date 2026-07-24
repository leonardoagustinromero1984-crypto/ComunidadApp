# Android CI — actualización del guard de migraciones a 047

## Causa raíz

El workflow `android-ci.yml` ejecuta `scripts/ci/m07_quality_checks.sh` antes de assemble/test/lint.
Ese script conservaba un tope histórico de migraciones (**032–036**), incompatible con el repo actual (máxima **047** tras M09–M12).

## Error observado

```text
Highest migration: 047
Expected highest migration 032–036, got 047
QUALITY CHECKS FAILED
```

Paso: **M07 local quality checks (catalog, migrations, secrets, SQL)**.

## Control desactualizado

En `scripts/ci/m07_quality_checks.sh`:

- Título: `Migration numbering 001–034`
- Condición: highest ∈ {032, 033, 034, 035, 036}

## Corrección

- Título: `Migration numbering 001–047`
- Condición estricta: highest **debe ser exactamente `047`**
- Si aparece `048` (u otra) sin actualizar el guard deliberadamente → el CI vuelve a fallar
- No se usó rango permisivo ni `continue-on-error`

## Validaciones ejecutadas

```text
bash -n scripts/ci/m07_quality_checks.sh
bash scripts/ci/m07_quality_checks.sh
```

Resultado esperado: quality checks PASSED y `Highest migration: 047`.

## Confirmaciones

- Migraciones **001–047** intactas (sin edición de SQL).
- **Sin migración 048**.
- Workflow sin debilitar: tests/lint siguen activos; JaCoCo permanece informativo.
- Advertencia **Node.js 20 deprecado** de Actions: informativa / no bloqueante (no es la causa del exit 1).
- Smoke funcional M12: **PENDIENTE EXTERNO** (no se declara M12 CERRADO).
- M13: no iniciado.
