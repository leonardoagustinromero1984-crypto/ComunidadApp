# M04 — Cierre Etapa 3: Persistencia, RLS, RPC y colas administrativas

**Fecha:** 2026-07-16  
**Rama:** `m04/etapa-3-persistencia-rls-rpc-colas`  
**Módulo:** M04 — Administración, Moderación y Soporte  
**Estado de entrada:** Etapa 2 consolidada (`9908b8bcb98ddc9ad26743ed4ce17180d7d72422`)  
**Spec:** `docs/03-modulos/M04-Etapa-3-Persistencia-RLS-RPC-y-Colas-Administrativas.md`

---

## 1. Rama y commits

| Ref | SHA / nota |
|-----|------------|
| Commit base Etapa 2 | `9908b8bcb98ddc9ad26743ed4ce17180d7d72422` |
| Rama de trabajo | `m04/etapa-3-persistencia-rls-rpc-colas` |
| WIP GPS/mapas/pagos | **No** incorporado |
| Merge a `main` | **No** |
| Etapa 4 / M05 | **No** iniciados |

---

## 2. Migración

| Ítem | Valor |
|------|-------|
| Archivo | `supabase/migrations/022_m04_administration_moderation_support_foundation.sql` |
| Numeración | Consecutiva a `021` |
| Edición de `001`–`021` | **No** |
| Remoto / staging | **PENDIENTE DE VALIDACIÓN REMOTA** |

### 2.1 Corrección crítica `content_reports` (primer bloque)

- Drop inmediato de políticas SELECT/UPDATE `using (true)` (sin ventana fail-open)
- Columnas compatibles: `priority`, `case_id`, `reason_code`, `reason_detail`, `duplicate_of_report_id`, `updated_at`
- Backfill determinista de `reason_code`
- Estados legacy + Etapa 2; targets legacy `POST`/`USER`/`COMMENT` (+ dominio)
- RLS: reporter propio INSERT/SELECT; staff SELECT por permiso; UPDATE/DELETE denegados
- Mutación staff solo vía RPC; `reporter_id` sensible (strip sin `moderation.view_sensitive`)

### 2.2 Tablas nuevas

- `moderation_cases`, `moderation_case_reports`, `moderation_actions`, `moderation_appeals`
- `moderation_evidence_refs`, `moderation_case_notes`
- `organization_verification_reviews`, `organization_verification_document_refs`
- `support_tickets`, `support_ticket_messages`
- `administrative_assignments`, `administrative_audit_log`

Escrituras directas revocadas; SELECT acotado por permisos / ownership; mensajes INTERNAL ocultos al solicitante.

### 2.3 Permisos M04 (seeds idempotentes)

Nuevos: `moderation.manage_cases|apply_actions|view_sensitive|review_appeals`,  
`organizations.review_verification|revoke_verification`,  
`support.view|manage|view_sensitive`

Matriz: MODERATOR / ADMIN / SUPERADMIN según spec.  
Roles M03 / AccountType / active_modules **no** conceden autoridad.

### 2.4 RPCs (SECURITY DEFINER, `search_path = public`, actor `auth.uid()`)

Reportes, casos, medidas, apelaciones, verificación, soporte y `list_administrative_events`.  
Auditoría dentro de la misma transacción (`m04_audit`).  
Conflictos: aplicador ≠ revisor de apelación; miembro org ≠ revisor de verificación.

---

## 3. Android

| Archivo | Rol |
|---------|-----|
| `SupabaseModerationRepository` | RPC moderación |
| `SupabaseOrganizationVerificationRepository` | RPC verificación |
| `SupabaseSupportRepository` | RPC soporte |
| `SupabaseAdministrativeAuditRepository` | Lectura audit; escritura cliente denegada |
| `M04SupabaseRpcSupport` | Mapeo errores / JSON |
| `DataProvider` | Supabase si `useSupabase`, else mocks |
| `PlatformSupabaseDataSource` | Legacy AdminModeration vía RPC (sin UPDATE/SELECT inseguro) |
| `Authorization.kt` / `ModerationAuthorization.kt` | Códigos + matriz M04 |

Sin pantallas ni rutas nuevas. Sin Storage administrativo. Sin bucket `leover`.

---

## 4. Pruebas y calidad

| Control | Resultado |
|---------|-----------|
| Doc SQL | `docs/04-calidad/M04-pruebas-persistencia-rls-rpc-colas.md` |
| `assembleDebug` | **SUCCESS** |
| `testDebugUnitTest` | **233** tests, **0** failures, **0** errors |
| `lintDebug` | **SUCCESS** |

---

## 5. Compatibilidad legacy

- `content_reports` sigue siendo la entrada (sin tabla duplicada)
- `AdminModerationScreen` continúa compilando; operaciones privilegiadas van por RPC
- `ACTIONED` aceptado en triage; **no** implica medida real
- Mapeo dominio `USER` ↔ `USER_PROFILE` en capa Kotlin

---

## 6. Riesgos y deuda

| Ítem | Estado |
|------|--------|
| Staging `014`–`022` | **PENDIENTE DE VALIDACIÓN REMOTA** |
| UI operativa completa | Etapa 4 |
| Storage evidencia física | M05 / decisión futura |
| Checklist SQL en remoto | Pendiente de acceso autorizado |

---

## 7. Checklist Etapa 3

- [x] Working tree / rama desde commit base
- [x] Sin editar `001`–`021`
- [x] Migración `022` consecutiva
- [x] `content_reports` ya no abierto a authenticated
- [x] Reporter crea/lee proyección propia
- [x] Staff por permisos M02; sensibles con permiso extra
- [x] Casos, medidas, apelaciones, verificación, soporte
- [x] Auditoría en RPC; deny-by-default
- [x] Repositorios Supabase + mocks
- [x] Legacy AdminModeration adaptado
- [x] Pruebas SQL documentadas + Android verdes
- [x] Build / lint verdes
- [x] Staging declarado honestamente
- [x] Sin UI nueva / sin M05 / sin merge a `main` / sin Etapa 4

---

## 8. Parada

Etapa 3 **cerrada a nivel código y calidad local**.

**No** iniciar Etapa 4.  
**No** iniciar M05.  
**No** merge a `main`.  
**No** afirmar validación remota sin evidencia.
