# M07 — Reporte de validación local Supabase (migraciones 001–032)

**Producto:** LeoVer  
**Fecha:** 2026-07-17  
**Actor técnico:** Auto (Cursor)  
**Commit base:** `78ffe32d8b50eb0f5b8cbfeb446426708265379c`  
**Rama:** `m07/validacion-local-y-staging-014-032`  
**Rama origen:** `m07/etapa-6-validacion-staging-cierre-final`

---

## 1. Conclusión local

```text
VALIDACIÓN SUPABASE LOCAL BLOQUEADA
```

**Dependencias faltantes (ambas):** Docker (CLI + daemon) y Supabase CLI.  
No se instalaron herramientas. No se ejecutó `supabase start` ni `supabase db reset`.  
No se inventó ejecución SQL local.

Suite Android/CI local del commit de referencia: **PASSED** (ver §8).

Staging remoto: **PENDIENTE DE VALIDACIÓN REMOTA** · **RELEASE BLOQUEADO** · **USERNAME NO REVALIDADO — STAGING PENDIENTE**.

Migración **033:** no creada (no hubo apply local ni defecto SQL observado).

---

## 2. Git

| Campo | Valor |
|---|---|
| HEAD | `78ffe32d8b50eb0f5b8cbfeb446426708265379c` |
| Working tree al crear rama | limpio |
| Merge a `main` | no realizado |
| M08 | no iniciado |

---

## 3. Detección de herramientas

| Herramienta | Resultado |
|---|---|
| Supabase CLI | **NOT FOUND** |
| Docker CLI | **NOT FOUND** |
| Docker daemon | **no** |
| `supabase/config.toml` | **ausente** |
| Seeds locales | **ninguno** |
| Puertos 54321–54324 | ninguno observado |

Comandos locales **no ejecutados** (por bloqueo):

```text
supabase start
supabase db reset
```

---

## 4. Migraciones en repositorio

| Campo | Valor |
|---|---|
| Archivos `NNN_*.sql` | **32** |
| Rango | `001` … `032` |
| Máxima | `032_m07_stage6_final_validation_hardening.sql` |
| Edición 001–032 en esta sesión | **ninguna** |
| Resultado por migración (apply local) | **NO EJECUTADO** (herramientas ausentes) |

---

## 5. Verificación SQL local (post-apply)

**Estado:** NO EJECUTADO — validación DB local bloqueada.

| Chequeo | Resultado |
|---|---|
| Versión máxima 032 | NO EJECUTADO |
| Numeración sin duplicados (repo) | PASS estático (32 archivos únicos) |
| 118 event keys | NO EJECUTADO en DB; quality script local: **118** |
| 28 metric keys | NO EJECUTADO en DB; quality script: **28** |
| 14 health checks | NO EJECUTADO en DB; quality script: **14** |
| Ocho permisos M07 | NO EJECUTADO en DB; quality script: **8** |
| `audit.view` no autoriza RPC M07 | NO EJECUTADO en DB (código/SQL en repo Etapa 6) |
| Gates list/health/evaluate | NO EJECUTADO en DB |
| `m07_record_metric` solo service_role | NO EJECUTADO en DB |
| RLS tablas M07 | NO EJECUTADO en DB |
| DML authenticated denegado | NO EJECUTADO en DB |
| Writers sin PUBLIC/anon/authenticated EXECUTE | NO EJECUTADO en DB |
| DEFINER + `search_path = public` | NO EJECUTADO en DB (chequeo estático quality script PASSED) |
| Metadata / event key desconocidos | NO EJECUTADO en DB |
| Auditoría append-only | NO EJECUTADO en DB |
| Errores sin stack/PII | NO EJECUTADO en DB |
| Métricas agregadas / health UNKNOWN | NO EJECUTADO en DB |
| Incidentes OPEN→ACK→RESOLVED | NO EJECUTADO en DB |
| Retención preview/execute/legal hold | NO EJECUTADO en DB |
| READY_SIMULATED / export filePending | NO EJECUTADO en DB |
| Sin tablas marketing/tracking | quality script estático PASSED |

Deudas documentadas (sin simular cierre):

```text
EXPORTACIÓN DE ARCHIVO PENDIENTE
INTEGRACIÓN M06 PENDIENTE
```

---

## 6. Defectos encontrados

| ID | Ámbito | Descripción | Corrección |
|---|---|---|---|
| L01 | Entorno | Docker CLI/daemon ausentes | Ninguna automática — requiere instalación manual del usuario |
| L02 | Entorno | Supabase CLI ausente | Ninguna automática — requiere instalación manual del usuario |
| L03 | Config | `supabase/config.toml` ausente | Requiere `supabase init`/config local **sin** link a producción |

Sin defectos SQL de migraciones observados (no hubo apply).

---

## 7. Correcciones realizadas

Ninguna. No se creó migración 033. No se editó código Kotlin/SQL/auth.

---

## 8. Tests / build / lint / JaCoCo / quality

| Check | Resultado |
|---|---|
| `:app:assembleDebug` | SUCCESS |
| `:app:testDebugUnitTest` | SUCCESS — **544** tests, 0 failures, 0 errors, 0 skipped |
| `:app:lintDebug` | SUCCESS |
| `:app:jacocoTestReport` | SUCCESS |
| `scripts/ci/m07_quality_checks.sh` | **PASSED** |

**JaCoCo (informativo, umbral no activado):**

| Counter | % |
|---|---|
| Line | **28.31%** |
| Instruction | **17.85%** |

(Branch/class no re-parseados en este paso; coherentes con cierre Etapa 6: branch 9.73%, class 27.69%.)

Quality summary: highest=032 · eventos 118 · métricas 28 · health 14 · permisos 8.

---

## 9. Staging (Fase B — solo detección)

| Evidencia | Resultado |
|---|---|
| Project ref linkeado | **ausente** |
| `config.toml` / `.supabase` | **ausente** |
| Secret store / env STAGING | **no detectado** |
| Historial remoto consultable | **no** |
| Confirmación staging ≠ producción | N/A |

**Qué falta para staging:** project ref de staging autorizado + método de acceso verificable (CLI link a staging, Dashboard/SQL, o secret store) **sin** usar producción.

No se ejecutó `supabase link`, `db push` ni apply remoto.

Estado vigente:

```text
PENDIENTE DE VALIDACIÓN REMOTA
RELEASE BLOQUEADO
USERNAME NO REVALIDADO — STAGING PENDIENTE
```

---

## 10. Decisión de release

```text
RELEASE BLOQUEADO
```

Motivos: validación Supabase local bloqueada; staging sin acceso autorizado; matriz remota no ejecutada.

---

## 11. Archivos

| Archivo | Acción |
|---|---|
| `docs/04-calidad/M07-reporte-validacion-local-supabase-001-032.md` | **creado** (este documento) |
| `M07-reporte-validacion-staging.md` | sin cambios (sigue pendiente) |
| Bitácora staging | no creada (Fase C no aplica) |

Sin commit (según instrucción: solo local + staging bloqueado → detener para revisión).
