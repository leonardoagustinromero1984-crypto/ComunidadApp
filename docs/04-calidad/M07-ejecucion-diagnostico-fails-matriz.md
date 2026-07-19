# M07 — Ejecución diagnóstico FAIL matriz staging

**Producto:** LeoVer
**Script (versión corregida v2):** `scripts/sql/m07_diagnose_staging_matrix_fails.sql`
**Proyecto:** LeoVer de **pruebas/staging** (ref terminado en `mizz`)
**Producción:** **NO** ejecutar

> Usar **esta** versión del script. La v1 fallaba con `42704 role "PUBLIC" does not exist`.

---

## Pasos

1. Abrir el Dashboard Supabase del proyecto LeoVer de pruebas.
2. Confirmar que el project ref termina en `mizz` (no producción).
3. Abrir **SQL Editor**.
4. Crear una query nueva.
5. Pegar el contenido completo **actualizado** de `scripts/sql/m07_diagnose_staging_matrix_fails.sql` (v2 con `aclexplode`).
6. Revisar que el script sea **solo lectura** (SELECT/CTE; sin INSERT/UPDATE/DELETE/DROP/ALTER/CREATE/GRANT/REVOKE).
7. Ejecutar.
8. Revisar resultados por sección (`A_` … `F_`), en especial:
   - `public_execute`
   - `anon_execute_direct`
   - `anon_execute_effective`
   - `anon_execute_via_public`
9. Descargar / exportar CSV o copiar tablas.
10. Entregar el resultado para la **decisión final** (matriz vs migración 034).

---

## Qué mirar

| Sección | Contenido |
|---|---|
| A | Writers internos + ACL PUBLIC/anon (directo/efectivo/vía PUBLIC) |
| B | Grants directos vía `aclexplode` + `information_schema` |
| C | Hits estilo matriz + breakdown ACL anon |
| D | Metadatos + definición + ACL de `org_hash_invitation_token` |
| E | Callers inferidos |
| F | Clasificación preliminar (sin cambio hasta CSV) |

---

## Criterios post-ejecución

- Si C muestra `_resolve_invitation_by_token` con `anon_execute_direct` → gap REVOKE anon → candidato 034.
- Si anon solo es efectivo vía PUBLIC → documentar herencia; no confundir con grant directo.
- Si D muestra INVOKER + `extensions.digest` + proconfig `<unset>` → FAIL DEFINER/search_path = falso positivo de matriz.
- No aplicar REVOKE/ALTER desde el Editor en esta etapa.
- No crear 034 hasta decisión explícita.

```text
MATRIZ SQL STAGING FAIL — 3 RESULTADOS EN DIAGNÓSTICO
VALIDACIÓN STAGING PENDIENTE
RELEASE BLOQUEADO
MIGRACIÓN 034 NO CREADA
```
