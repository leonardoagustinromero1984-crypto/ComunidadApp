# Android CI — actualización del guard de migraciones (047 → 048)

## Contexto histórico (047)

El workflow `android-ci.yml` ejecuta `scripts/ci/m07_quality_checks.sh` antes de assemble/test/lint.
Tras M09–M12 el techo pasó a **047**. Documentación previa de ese hotfix: ver historial abajo.

## Actualización deliberada M13 Bloque 2 → 048

Al crear `048_m13_sightings_and_match_candidates.sql`, el guard se actualizó **deliberadamente**:

| Campo | Antes | Ahora |
|-------|-------|-------|
| Título | `Migration numbering 001–047` | `Migration numbering 001–048` |
| Condición | highest == `047` | highest == `048` |
| Techo futuro | falla si aparece 048 sin update | falla si aparece **049** sin update |

No se usó rango permisivo ni `continue-on-error`. El workflow CI **no** se debilitó.

## Validaciones

```text
bash -n scripts/ci/m07_quality_checks.sh
bash scripts/ci/m07_quality_checks.sh
```

Resultado esperado: quality checks PASSED y `Highest migration: 048`.

## Confirmaciones

- Migraciones **001–047** intactas (sin edición de SQL).
- Migración **048** creada (M13); **no aplicada remotamente**.
- Sin migración **049**.
- Guardas estáticas de highest (M07/M08/M12) alineadas a 048 / sin 049.
- Smoke funcional M12: **PENDIENTE EXTERNO**.
- M12 cierre oficial: **PENDIENTE**.
- M13 Bloque 2: **CERRADO LOCALMENTE**.

---

## Historial — hotfix a 047 (referencia)

### Causa raíz original

El script conservaba un tope histórico (**032–036**), incompatible con máxima **047**.

### Error observado

```text
Highest migration: 047
Expected highest migration 032–036, got 047
QUALITY CHECKS FAILED
```

### Corrección 047

- Condición estricta: highest **exactamente `047`**
- Luego M13 B2 lo elevó a **048** (este documento).

### Otros hotfixes CI (sin debilitar)

- Tareas Gradle explícitas `localDebug` (flavors).
- M09 ID collision (`AtomicLong`) en mock de postulaciones.
- Node.js 20 deprecado en Actions: informativo / no bloqueante.
