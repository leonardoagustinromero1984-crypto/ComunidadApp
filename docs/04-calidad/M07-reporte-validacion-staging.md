# M07 — Reporte de validación staging

**Fecha:** 2026-07-18  
**Producto:** LeoVer  
**Rama staging:** `m07/validacion-staging-014-032`  
**Rama lint 033:** `m07/correccion-lint-staging-033`  
**Rama oficial local consolidada:** `m07/validacion-local-y-staging-014-032`  
**Commit local validado (001–032):** `80379b1201b3a31e94a572130a44cf07304a87ac`  
**Project ref staging (últimos 4):** `mizz`  
**Entorno objetivo:** staging no productivo  
**Actor técnico:** Auto (Cursor)  
**Estado general:** **PENDIENTE DE VALIDACIÓN REMOTA** (033 lista, no aplicada)  
**Release:** **RELEASE BLOQUEADO**

---

## 0. Estado declarado (obligatorio)

```text
VALIDACIÓN SUPABASE LOCAL PASS
VALIDACIÓN LOCAL 001–033 PASS
CORRECCIÓN 033 LISTA PARA STAGING
PENDIENTE DE VALIDACIÓN REMOTA
RELEASE BLOQUEADO
USERNAME NO REVALIDADO — STAGING PENDIENTE
EXPORTACIÓN DE ARCHIVO PENDIENTE
INTEGRACIÓN M06 PENDIENTE
```

**No es STAGING PASS.** Apply remoto 033: **NO**. Producción: **NO** tocada. M08: **NO**. Merge a `main`: **NO**.

Detalle lint: `docs/04-calidad/M07-reporte-correccion-lint-033.md`.

---

## 0.1 Apply remoto 001–032 y lint (actualización)

| Hecho | Resultado |
|---|---|
| Reset/apply remoto 001–032 | **PASS** (confirmado por propietario; exit 0) |
| Historial remoto vs local 001–032 | **alineado** |
| Backup previo | `...\LeoVerBackups\2026-07-18_pre_M07` (fuera del repo) |
| `db lint` remoto post-032 | **FAIL** — 7 errores (L05–L11) |
| Migración 033 | creada; validada local 2× reset + 2× lint exit 0 |
| Apply remoto 033 | **PENDIENTE** |

### Errores lint corregidos en 033

L05 `is_username_available` · L06 `add_reputation_points` · L07 `complete_profile_onboarding` · L08 `org_hash_invitation_token` · L08b `_resolve_invitation_by_token` (STABLE→VOLATILE) · L09 `invite_organization_member` · L10 `m06_claim_outbox` · L11 `m06_claim_push_deliveries`.

Warnings (~22): backlog (actor no leído; IMMUTABLE/STABLE mismatch; etc.) — no bloquean `--fail-on error`.

---

## 1. Consolidación local previa

| Paso | Resultado |
|---|---|
| Fast-forward `m07/validacion-local-y-staging-014-032` ← corrección | **OK** (`78281e6` → `80379b1`) |
| HEAD oficial | `80379b1201b3a31e94a572130a44cf07304a87ac` |
| Rama staging creada | `m07/validacion-staging-014-032` @ mismo commit |
| Working tree al crear rama | limpio |

---

## 2. Evidencia local PASS (resumen)

| Evidencia | Valor |
|---|---|
| Docker / CLI | 29.6.1 / 2.109.1 |
| 2× `db reset --local` | APPLY OK 001–032 |
| Historial | 32 versiones, max 032, sin dupes |
| Catálogos | 118 / 28 / 14 |
| Permisos M07 | 8 |
| RLS / grants / DEFINER | PASS |
| Incidentes / retención / export | PASS (`AUTHORIZED` + `filePending`) |
| `supabase test db` | NOTESTS (sin pgTAP) |
| Android | 544 tests · assemble/lint/jacoco/quality PASS |
| Migración 033 | no creada |

### Correcciones incluidas en el commit validado

| ID | Migración / ámbito | Corrección |
|---|---|---|
| L01 | 020 | 10× `citext` → `extensions.citext`; `SET search_path = public` conservado |
| L02 | 029 | BOM UTF-8 eliminado |
| L03 | 029 | delimitadores `$$` truncados restaurados |
| L04 | 031 | `DROP FUNCTION` antes de cambiar retorno a `jsonb` |

Config local Windows (no remota): puertos `5532x`; analytics/studio locales off.

AuthRepository / domain/auth / UsernameValidators: **intactos**.

---

## 3. Auditoría de acceso staging (sanitizada)

| Elemento | Estado |
|---|---|
| `supabase/.temp` / `project-ref` | **AUSENTE** (sin CLI link) |
| `.supabase` | **AUSENTE** |
| `supabase/.env` | **AUSENTE** |
| `.env` / `.env.local` / `.env.staging` | **AUSENTE** |
| `gradle.properties` keys SUPABASE | **AUSENTE** |
| `local.properties` | **PRESENTE** (local, no commiteado) |
| Keys en `local.properties` | `sdk.dir`, `SUPABASE_URL`, `SUPABASE_ANON_KEY` |
| `SUPABASE_ANON_KEY` | **PRESENTE** (cliente Android) |
| service_role en `local.properties` | **AUSENTE** |
| Env vars `SUPABASE_ACCESS_TOKEN` / `SUPABASE_DB_URL` / `SUPABASE_DB_PASSWORD` / `SUPABASE_PROJECT_REF` | **AUSENTE** |
| CI (`.github/workflows`) secretos Supabase | **AUSENTE** (CI en modo mock; nota staging PENDIENTE) |
| Documentación flavors staging/prod | ADR-0005: **sin flavors** staging/production aún |
| `supabase/config.toml` | solo `project_id` local `ComunidadApp`; **sin** project ref remoto |

### Project ref detectado (enmascarado)

| Campo | Valor |
|---|---|
| Origen | `local.properties` → `SUPABASE_URL` host `*.supabase.co` |
| Longitud ref | 20 |
| Últimos 4 caracteres | `mizz` |
| Identidad de entorno | **NO VERIFICADA** (no hay etiqueta staging/prod) |
| staging ≠ producción | **NO CONFIRMABLE** con evidencia actual |

No se imprimen URL completa, anon key, JWT ni connection strings.

---

## 4. Clasificación

```text
B. STAGING CONFIGURADO PERO NO VERIFICADO
```

**Motivo:** existe configuración cliente (`SUPABASE_URL` + anon en `local.properties`), pero:

1. no hay evidencia inequívoca de que el proyecto sea staging;
2. no hay evidencia inequívoca de que no sea producción;
3. no hay `supabase link` / access token / DB password para operaciones de migración remota;
4. CI no inyecta credenciales remotas.

### Acciones bloqueadas por clasificación B

- no `supabase login`
- no `supabase link`
- no `db push` / `--linked`
- no `migration repair`
- no reset remoto
- no SQL remoto
- no adivinar credenciales
- no tocar ningún proyecto Supabase

---

## 5. Historial remoto

| Campo | Valor |
|---|---|
| Consulta `supabase migration list --linked` | **NO EJECUTADA** (sin link verificado) |
| Versión máxima remota | **DESCONOCIDA** |
| Migraciones remotas | **DESCONOCIDAS** |
| Pendientes estimadas 014–032 | **19 archivos locales listos**; remoto desconocido → asumir pendientes hasta historial real |
| Divergencias / dupes remotos | **NO EVALUABLES** |
| `extensions.citext` remoto | **NO VERIFICADO** |
| Backup / snapshot | **PENDIENTE** (obligatorio antes de apply) |

Secuencia local candidata (aplicar solo lo que el historial remoto marque como pendiente):

```text
014 → 015 → 016 → 017 → 018 → 019 → 020 → 021 → 022 → 023 → 024 → 025 → 026 → 027 → 028 → 029 → 030 → 031 → 032
```

---

## 6. Riesgos

| Riesgo | Mitigación |
|---|---|
| Aplicar a producción por error | Exigir confirmación escrita staging ≠ prod + ref distinto |
| Reaplicar migración ya presente | Leer historial remoto; no repair sin evidencia |
| 020 citext / 029 BOM / 031 DROP no aplicados remotamente | Apply ordenado del commit `80379b1` |
| Sin backup | STOP — no apply |
| Client anon sin privilegios de migración | Requiere access token + link staging del propietario |

---

## 7. Plan de apply (documentado — NO ejecutado)

Ver procedimiento detallado: `docs/04-calidad/M07-procedimiento-apply-staging-014-032.md`.

Orden resumido:

1. Verificar staging ≠ producción (autorización escrita + ref).
2. Backup/snapshot previo.
3. `supabase migration list --linked` (solo tras link staging verificado).
4. Confirmar 001–013 remotas.
5. Determinar pendientes reales entre 014–032.
6. Detectar contenido previo distinto en 014–032 ya aplicadas.
7. No reaplicar existentes; no repair sin autorización.
8. Apply solo pendientes.
9. Verificar historial post-apply.
10. Validaciones SQL + smoke Android.
11. Registrar rollback.
12. Mantener release bloqueado hasta PASS completo.

### Comandos potenciales (pendientes — no ejecutados)

```text
# Solo tras staging verificado y link existente autorizado:
supabase migration list --linked
supabase db push --linked --dry-run
# Si la CLI 2.109.1 no admite --dry-run en db push: documentar y NO sustituir por apply real.
supabase db push --linked
```

**Apply ejecutado en esta sesión:** NO  
**Dry-run ejecutable ahora:** NO (falta link staging verificado)  
**Backup disponible:** PENDIENTE (acción del propietario)

---

## 8. Plan de validación post-apply (pendiente)

| Área | Criterio |
|---|---|
| Catálogos | 118 / 28 / 14 sin dupes; igual Kotlin |
| Permisos | 8 M07; gates 032; usuario común denegado |
| RLS / grants / DEFINER | como local PASS |
| Incidentes / retención / export | como local; deuda filePending |
| M06 | `m07.incident.staff_notification` catalogado; sin envío simulado |
| Username | revalidar sin corregir en M07 |
| Smoke Android | build/tests contra staging autorizado |

---

## 9. Plan de rollback (pendiente de propietario)

| Opción | Notas |
|---|---|
| Restaurar snapshot/backup pre-apply | Preferida |
| Point-in-time recovery (si plan Supabase lo permite) | Registrar ID/hora |
| No “desaplicar” migraciones a mano | Evitar repair destructivo |

Sin backup registrado: **apply prohibido**.

---

## 10. Acciones necesarias del propietario del proyecto

1. Confirmar por escrito el project ref de **staging** y que **≠ producción** (ref prod enmascarado o declaración formal).  
2. Proveer acceso CLI autorizado (`SUPABASE_ACCESS_TOKEN` o login del owner) **sin** commitear secretos.  
3. Ejecutar `supabase link` solo al staging verificado (fuera de esta sesión automatizada).  
4. Crear backup/snapshot y registrar ID.  
5. Ejecutar procedimiento `M07-procedimiento-apply-staging-014-032.md`.  
6. Devolver evidencias sanitizadas para actualizar este reporte a PASS/FAIL remoto.

---

## 11. Matriz remota (estado)

Todos los casos remotos de las secciones históricas (acceso, historial, backup, apply, catálogos, permisos, auditoría, métricas, health, incidentes, retención, export, M06, UI, Edge, username):

**NO EJECUTADO** — bloqueados por clasificación **B**.

---

## 12. Condición de release

```text
RELEASE BLOQUEADO
```

Foundation M07 cerrado **localmente**. Release de producto bloqueado hasta validación remota PASS demostrable.

---

## 13. Limitaciones de este reporte

- No se inventaron resultados de staging.  
- No se usó producción.  
- No se inició M08.  
- No se hizo merge a `main`.  
- No se crearon links nuevos.  
- No se ejecutó `supabase login`.
