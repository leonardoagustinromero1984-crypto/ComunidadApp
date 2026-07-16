# M04 — Cierre Etapa 5: Validación, staging, calidad y cierre final

**Fecha:** 2026-07-16  
**Rama:** `m04/etapa-5-validacion-cierre`  
**Módulo:** M04 — Administración, Moderación y Soporte  
**Estado de entrada:** Etapa 4 consolidada (`41e0d65cc366602959bd8b1292701f7633213a29`)  
**Spec:** `docs/03-modulos/M04-Etapa-5-Validacion-Staging-Calidad-y-Cierre-Final.md`

---

## 1. Git

| Ref | Valor |
|-----|-------|
| Commit base | `41e0d65cc366602959bd8b1292701f7633213a29` |
| Rama | `m04/etapa-5-validacion-cierre` |
| Working tree inicial | Limpio (salvo spec Etapa 5 untracked) |
| Merge a `main` | **No** |
| M05 | **No** iniciado |
| WIP GPS/mapas/pagos | **No** incorporado |
| Producción | **No** usada |

---

## 2. Auditoría integral (resumen)

### Confirmado OK (estático)

- Migraciones `014`–`022` consecutivas; `001`–`021` no editadas por M04.
- `022` elimina SELECT/UPDATE `using (true)` de `content_reports`.
- RPCs `SECURITY DEFINER` con `search_path = public` y actor `auth.uid()`.
- Conflictos: aplicador ≠ revisor de apelación; miembro org ≠ revisor de verificación.
- Notas internas / auditoría: mutación cliente denegada; lectura vía RPC.
- Requester support: mensajes `INTERNAL` no en proyección requester (RPC).
- Android: gates deny-by-default, deep links AccessDenied, sin AccountType/modules/roles M03 como autoridad, sin logs PII en VMs/repos M04, ruta `admin_moderation` compatible.
- Mocks vs Supabase vía `DataProvider`.

### Defectos reales encontrados y corregidos

| ID | Severidad | Defecto | Corrección |
|----|-----------|---------|------------|
| D1 | Alta | SELECT directo `content_reports` exponía `reporter_id` a staff con `moderation.view` | Migración `023`: SELECT solo reporter |
| D2 | Alta | RLS `support_ticket_messages` bypasseaba categoría PRIVACY/SAFETY | `023`: policy con check del ticket padre + `support.view_sensitive` |
| D3 | Media | `get_organization_verification_review` filtraba nota top-level pero filtraba `row_to_json(r)` con `review_note` | `023`: proyección allowlist + SELECT tabla denegado (RPC only) |
| D4 | Media | `ProfileViewModel.logout` no limpiaba estado administrativo | Llama `AdministrativeSessionCleanup` + org context |
| D5 | Media | Race doble create case / guards de mutación / estado stale en refresh | Locks + clear Loading + permisos por operación |
| D6 | Media | Parser Kotlin conservaba `review_note` anidado si top-level null | Siempre usa top-level `review_note` |

### No corregido (deuda aceptada)

- `MockSupportRepository` sin actor `auth.uid()`: no replica ownership/permisos server-side; UI + Supabase RPC protegen producción. Residual solo con `useSupabase = false`.

---

## 3. Migración nueva

| Archivo | Motivo |
|---------|--------|
| `supabase/migrations/023_m04_sensitive_projection_rls_fix.sql` | Defectos reales D1–D3 de `022`; alcance mínimo; **no** edita `022` |

Prueba de regresión: `M04Migration023SensitiveProjectionRegressionTest`.

---

## 4. Archivos tocados (Etapa 5)

**Creados:** migración `023`, tests de regresión/logout, docs de cierre y staging, spec Etapa 5.

**Modificados:** Profile logout, ViewModels (casos, reportes, apelaciones, soporte admin, auditoría, verificación), mapper verificación Supabase.

**Eliminados:** ninguno.

---

## 5. Calidad local

| Control | Resultado |
|---------|-----------|
| `assembleDebug` | SUCCESS (ver commit / log de etapa) |
| `testDebugUnitTest` | **261** tests, **0** failures, **0** errors |
| `lintDebug` | SUCCESS |
| Staging remoto | **PENDIENTE DE VALIDACIÓN REMOTA** |

Detalle staging: `docs/04-calidad/M04-reporte-validacion-staging.md`.

---

## 6. Seguridad (confirmado en auditoría)

- Sin service role en Android.
- Sin bucket `leover` / Storage administrativo nuevo.
- Sin IA de moderación / Hilt / Retrofit / otro backend.
- UI no sustituye autorización server-side.
- Release bloqueado sin staging PASS.

---

## 7. Checklist Etapa 5

- [x] Commit base verificado
- [x] Rama limpia sin WIP
- [x] Migraciones `014`–`022` revisadas; `023` correctiva documentada
- [x] Sin editar migraciones aplicadas
- [x] Defectos reales corregidos
- [x] Build / tests / lint (locales)
- [x] Staging marcado honestamente pendiente
- [x] Documentos de salida creados
- [x] Sin M05 / sin merge a `main`

---

## 8. Parada

Etapa 5 **cerrada a nivel código y calidad local**.  
Cierre de módulo: `docs/02-arquitectura/M04-cierre-final.md`.

**No** iniciar M05.  
**No** merge a `main`.
