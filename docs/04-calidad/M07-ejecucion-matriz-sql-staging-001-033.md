# M07 — Ejecución matriz SQL staging 001–033

**Producto:** LeoVer
**Entorno:** staging / pruebas (project ref terminado en `mizz`)
**Script:** `scripts/sql/m07_validate_staging_001_033.sql`
**Producción:** **NO** ejecutar
**Migración 034:** **NO** crear en esta etapa

---

## Objetivo

Validar de forma segura y reproducible en staging:

- historial 001–033;
- catálogos 118 / 28 / 14 y 8 permisos M07;
- correcciones de funciones en 033;
- SECURITY DEFINER / `search_path`;
- RLS / grants;
- gates de permisos;
- auditoría, métricas, health, incidentes, retención, exportación;
- deudas M06 / archivo (BACKLOG).

El script es de **solo lectura** salvo probes en subtransacciones con aborto (sin residuo permanente).

---

## Pasos de ejecución (manual)

1. Abrir Dashboard Supabase.
2. Confirmar proyecto **LeoVer de pruebas/staging** (ref terminado en `mizz`). No usar producción.
3. Abrir **SQL Editor**.
4. Crear una query nueva.
5. Pegar el contenido completo de `scripts/sql/m07_validate_staging_001_033.sql`.
6. Revisar que el script **no** contenga secrets, tokens, URLs completas, ni comandos destructivos (`DROP` / `TRUNCATE` / `DELETE` / `ALTER` / `CREATE` / `GRANT` / `REVOKE` permanentes). Se permiten `TEMP` y subtransacciones con rollback.
7. Ejecutar la query.
8. Exportar o copiar la tabla de resultados (`check_group`, `check_name`, `expected`, `actual`, `status`, `details`).
9. **No** ejecutar en producción.
10. Entregar resultados para cerrar **STAGING PASS** o **STAGING FAIL**.

---

## Estados permitidos

| Status | Uso |
|---|---|
| `PASS` | Criterio demostrado |
| `FAIL` | Criterio incumplido |
| `NOT_EXECUTED` | No ejecutable sin JWT/fixture; justificado en `details` |
| `BACKLOG` | Deuda conocida (warnings lint, archivo, M06) |

---

## Criterios de cierre

### PASS (matriz)

- Ningún check con `status = FAIL`.
- Checks obligatorios en `PASS`: historial 33/033, catálogos 118/28/14/8, RLS habilitada en tablas M07, grants sin escritura cliente, definiciones 033, DEFINER/`search_path`, gates 032.
- `NOT_EXECUTED` y `BACKLOG` deben estar explicados en `details`.

### FAIL (matriz)

- Cualquier conteo incorrecto (historial o catálogos).
- RLS deshabilitada en tabla M07 inventariada.
- Grant indebido (INSERT/UPDATE/DELETE authenticated/anon, o EXECUTE indebido).
- Función 033 incorrecta (citext/digest/claim/VOLATILE/etc.).
- Historial inconsistente (≠33, max≠033, dupes o faltantes).
- Error SQL al ejecutar el script.

---

## Deudas explícitas (no bloquean apply/lint ya PASS)

```text
EXPORTACIÓN DE ARCHIVO PENDIENTE
INTEGRACIÓN M06 PENDIENTE
WARNINGS NO BLOQUEANTES: BACKLOG (~22)
```

---

## Prohibiciones

- No `db push` / `db reset --linked` / migration repair.
- No migración 034.
- No modificar datos permanentes.
- No crear usuarios reales ni tocar `auth.users`.
- No exponer URL completa, project ref completo, tokens, JWT, anon key, service_role o DB password.
- No inventar resultados: pegar evidencia tabular real.

---

## Tras la ejecución

Actualizar `docs/04-calidad/M07-reporte-validacion-staging.md` con:

- `MATRIZ SQL STAGING PASS` o `FAIL`;
- conteos reales;
- lista de `FAIL` / `NOT_EXECUTED` / `BACKLOG`.

Hasta entonces:

```text
APPLY STAGING 001–033 PASS
DB LINT STAGING PASS
MATRIZ SQL STAGING PENDIENTE DE EJECUCIÓN
RELEASE BLOQUEADO
```
