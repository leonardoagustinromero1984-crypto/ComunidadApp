# M07 — Ejecución diagnóstico FAIL matriz staging

**Producto:** LeoVer  
**Script:** `scripts/sql/m07_diagnose_staging_matrix_fails.sql`  
**Proyecto:** LeoVer de **pruebas/staging** (ref terminado en `mizz`)  
**Producción:** **NO** ejecutar

---

## Pasos

1. Abrir el Dashboard Supabase del proyecto LeoVer de pruebas.
2. Confirmar que el project ref termina en `mizz` (no producción).
3. Abrir **SQL Editor**.
4. Crear una query nueva.
5. Pegar el contenido completo de `scripts/sql/m07_diagnose_staging_matrix_fails.sql`.
6. Revisar que el script sea **solo lectura** (SELECT/CTE; sin INSERT/UPDATE/DELETE/DROP/ALTER/CREATE/GRANT/REVOKE).
7. Ejecutar.
8. Revisar resultados por sección (`A_` … `F_`).
9. Descargar / exportar CSV o copiar tablas.
10. Entregar el resultado para la **decisión final** (matriz vs migración 034).

---

## Qué mirar

| Sección | Contenido |
|---|---|
| A | Writers internos: DEFINER/INVOKER, search_path, EXECUTE efectivo |
| B | Grants **directos** PUBLIC/anon/authenticated/service_role |
| C | Filas que explican `internal_writers_anon_execute = 1` |
| D | Metadatos + definición + grants de `org_hash_invitation_token` |
| E | Callers inferidos de `org_hash_invitation_token` |
| F | Clasificación preliminar |

---

## Criterios post-ejecución

- Si C muestra `_resolve_invitation_by_token` → confirma gap de REVOKE anon → candidato 034.
- Si D muestra INVOKER + `extensions.digest` + proconfig `<unset>` → FAIL DEFINER/search_path = falso positivo de matriz.
- No aplicar REVOKE/ALTER desde el Editor en esta etapa.
- No crear 034 hasta decisión explícita.
