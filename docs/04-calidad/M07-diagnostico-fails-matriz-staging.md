# M07 — Diagnóstico de FAIL matriz SQL staging

**Fecha:** 2026-07-19  
**Producto:** LeoVer  
**Rama:** `m07/diagnostico-matriz-staging-fails`  
**Matriz origen:** `scripts/sql/m07_validate_staging_001_033.sql`  
**Script diagnóstico:** `scripts/sql/m07_diagnose_staging_matrix_fails.sql` (read-only)  
**Entorno:** staging / pruebas (ref …`mizz`)  
**Cambios remotos en esta tarea:** **NO**

---

## 1. Resultados originales (matriz)

| check_name | expected | actual | status |
|---|---|---|---|
| `internal_writers_anon_execute` | 0 | 1 | FAIL |
| `org_hash_invitation_token_search_path` | `search_path=public` | `<unset>` | FAIL |
| `org_hash_invitation_token(p_token text)_security_definer` | SECURITY DEFINER | INVOKER | FAIL |

Resumen matriz reportado: 261 filas · 249 PASS · 3 FAIL · 3 NOT_EXECUTED · 5 BACKLOG.

---

## 2. Cómo los calcula la matriz

### Writers internos (`v_internal`)

```text
m07_write_audit_event
m07_validate_metadata
m07_validate_metric_dimensions
m07_sanitize_health_details
m07_latest_metric_value
m06_claim_outbox
m06_claim_push_deliveries
m07_record_metric
_resolve_invitation_by_token
```

`internal_writers_anon_execute` cuenta filas en `information_schema.routine_privileges` con:

- `specific_schema = 'public'`
- `grantee = 'anon'`
- `privilege_type = 'EXECUTE'`
- `routine_name` ∈ lista anterior

Es **grant directo** a `anon` (no herencia vía PUBLIC).

### Inventario DEFINER (`v_names`)

Incluye, entre otras, `org_hash_invitation_token` y exige a **todas**:

- `SECURITY DEFINER`
- `SET search_path = public`

sin excepción para helpers puros.

---

## 3. FAIL 1 — `internal_writers_anon_execute`

### Función probable

`public._resolve_invitation_by_token(text)`

### Evidencia de migraciones

| Migración | Grants |
|---|---|
| 020 | `revoke all ... from public` **solo** (no `anon`) |
| 033 (recreate VOLATILE) | igual: `revoke all ... from public` **solo** |

Resto de writers internos típicos: `revoke ... from public, anon, authenticated`.

En proyectos Supabase, `ALTER DEFAULT PRIVILEGES` / defaults suelen otorgar EXECUTE a `anon`. Si solo se revoca `PUBLIC`, **`anon` puede conservar EXECUTE directo**.

### Finalidad

Resolver invitación por token en claro (hash → fila; puede marcar EXPIRED). Es **SECURITY DEFINER** interno, no RPC pública.

### Clasificación preliminar

**DEFECTO_REAL** (gap de grants / hardening de superficie).

- Matriz: **correcta** al exigir 0 EXECUTE anon en internos.
- Riesgo: probing de tokens por rol `anon` (sin sesión).
- Fix típico (futuro, no ahora): `REVOKE ALL ON FUNCTION ... FROM anon, authenticated;` en migración **034** autorizada.
- Nested callers DEFINER del mismo owner siguen pudiendo ejecutar.

Confirmar con sección **C** del script diagnóstico.

---

## 4. FAIL 2 y 3 — `org_hash_invitation_token`

### Definición

| Origen | Forma |
|---|---|
| 020 | `language sql immutable` — `digest(...)` sin schema |
| 033 L08 | misma forma; `extensions.digest(...)` calificado |

**No** es `SECURITY DEFINER`. **No** declara `search_path`.

### Análisis

| Pregunta | Respuesta |
|---|---|
| ¿Necesita SECURITY DEFINER? | **No** — solo calcula hash; no lee/escribe tablas. |
| ¿INVOKER es más seguro? | **Sí** — evita privilegios elevados innecesarios. |
| ¿Objetos no calificados? | Tras 033: **`extensions.digest`** calificado. |
| ¿Necesita `SET search_path = public`? | **No** para corrección funcional; opcional. |
| ¿La matriz impone expectativa sin respaldo? | **Sí** — metió el helper en `v_names` con regla DEFINER+search_path genérica. |

Callers: `invite_organization_member`, `_resolve_invitation_by_token` (DEFINER + `search_path=public`).

### Clasificación preliminar

| Check | Clasificación |
|---|---|
| `..._security_definer` → INVOKER | **FALSO_POSITIVO** (matriz) |
| `..._search_path` → `<unset>` | **FALSO_POSITIVO** (matriz); a lo sumo **HARDENING_NO_BLOQUEANTE** si se desea `SET search_path = public` |

**No** convertir a DEFINER “para pasar el test”.

---

## 5. Resumen de clasificaciones

| FAIL | Clasificación | Matriz incorrecta | 034 necesaria |
|---|---|---|---|
| `internal_writers_anon_execute` | DEFECTO_REAL (grants) | No | **Pendiente** — sí si se confirma `_resolve...` con anon |
| `org_hash_..._security_definer` | FALSO_POSITIVO | **Sí** | **No** (no forzar DEFINER) |
| `org_hash_..._search_path` | FALSO_POSITIVO | **Sí** | **No** (opcional hardening) |

---

## 6. Riesgos

| Riesgo | Nivel | Nota |
|---|---|---|
| Anon ejecuta `_resolve_invitation_by_token` | Medio-bajo | Probing de tokens; corregir con REVOKE en 034 |
| Forzar DEFINER en org_hash | Alto si se hace mal | Superficie innecesaria; **evitar** |
| Declarar STAGING PASS ahora | — | **Prohibido** hasta decisión post-CSV |

---

## 7. Siguiente paso manual

1. Ejecutar guía `docs/04-calidad/M07-ejecucion-diagnostico-fails-matriz.md`.
2. Pegar `scripts/sql/m07_diagnose_staging_matrix_fails.sql` en SQL Editor.
3. Descargar CSV / copiar resultados (secciones A–F).
4. Confirmar función en C.
5. Decidir: ajustar matriz (org_hash) ± migración 034 solo para REVOKE anon.

Estado:

```text
MATRIZ SQL STAGING FAIL — 3 RESULTADOS EN DIAGNÓSTICO
VALIDACIÓN STAGING PENDIENTE
RELEASE BLOQUEADO
```
