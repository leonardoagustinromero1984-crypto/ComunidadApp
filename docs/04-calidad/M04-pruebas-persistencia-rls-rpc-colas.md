# M04 — Pruebas de persistencia, RLS, RPC y colas administrativas (Etapa 3)

**Módulo:** M04 — Administración, Moderación y Soporte  
**Alcance:** SQL/RLS documentado (`022`) + suite unitaria Android  
**Estado remoto:** Migraciones `014`–`022` **PENDIENTE DE VALIDACIÓN REMOTA** (no se afirma deploy ni checklist en staging/producción en esta etapa).

---

## 1. Suite SQL / RLS (manual / staging)

Ejecutar solo en proyecto de staging o local. **No** correr destructivos en producción.

### 1.1 `content_reports` (corrección crítica)

| Caso | Esperado |
|------|----------|
| Políticas SELECT/UPDATE `using (true)` | **Eliminadas** al inicio de `022` |
| Autenticado lee reporte ajeno (SELECT directo) | 0 filas (salvo permiso `moderation.view` / `manage_reports`) |
| Autenticado UPDATE directo | Denegado (`using (false)` + revoke update) |
| Reporter INSERT con `reporter_id = auth.uid()` | OK |
| Reporter INSERT con otro `reporter_id` | Denegado |
| `get_my_content_reports` | Solo filas propias |
| `list_moderation_queue` sin `moderation.view` | `FORBIDDEN` |
| `list_moderation_queue` sin `view_sensitive` | `reporter_id` null en proyección |
| Con `moderation.view_sensitive` | `reporter_id` visible |
| Targets legacy `POST` / `USER` / `COMMENT` | Aceptados por `create_content_report` |
| `mark_content_report_duplicate` sin referencia / self | `VALIDATION` / `NOT_FOUND` |
| `triage_content_report` con `ACTIONED` / `REVIEWED` | Aceptado (legacy); `ACTIONED` ≠ medida real |

### 1.2 Casos, medidas y apelaciones

| Caso | Esperado |
|------|----------|
| Escritura directa en tablas `moderation_*` | INSERT/UPDATE/DELETE revocados |
| `attach_report_to_moderation_case` doble caso activo | Bloqueado |
| `assign_moderation_case` sin `manage_cases` | `FORBIDDEN` |
| `apply_moderation_action` sin `apply_actions` | `FORBIDDEN` |
| Cuenta SUSPENDED/BANNED | Requiere además `users.change_status`; historial M02 atómico |
| Org SUSPENDED | Actualiza estado M03 + historial en misma transacción |
| `VERIFICATION_REVOKED` | Requiere `organizations.revoke_verification` |
| Medida temporal sin `expires_at` | `VALIDATION` |
| `submit_moderation_appeal` por no afectado | Bloqueado |
| Segunda apelación activa | Bloqueado |
| `review_moderation_appeal` por quien aplicó la medida | `CONFLICT` / denegado |
| Decisión sin motivo | `VALIDATION` |
| Fallo mid-RPC | Rollback completo (acción + estado + audit) |

### 1.3 Verificación de organizaciones

| Caso | Esperado |
|------|----------|
| Cola sin `organizations.review_verification` | `FORBIDDEN` |
| Miembro ACTIVE de la org como revisor | `CONFLICT` / denegado |
| APPROVE/REJECT solo desde org `PENDING` | OK / `VALIDATION` |
| `REQUEST_MORE_INFORMATION` | Org permanece `PENDING` (no `VERIFIED`) |
| REVOKE solo desde `VERIFIED` + permiso revoke | OK |
| Notas / document refs | No públicos; sin URLs `http` permanentes |

### 1.4 Soporte

| Caso | Esperado |
|------|----------|
| Usuario crea ticket propio | OK vía `create_support_ticket` |
| SELECT ticket ajeno | 0 filas |
| `get_support_ticket_for_requester` | Mensajes solo `REQUESTER_VISIBLE` |
| Mensaje `INTERNAL` | Nunca en proyección requester |
| Staff sin `support.view` | No ve cola |
| Prioridad / asignación | Solo staff con `support.manage` |
| CLOSE sin resolución ni motivo | `VALIDATION` |
| Categorías PRIVACY/SAFETY | Tratamiento sensible (`support.view_sensitive`) |

### 1.5 Permisos y auditoría

| Caso | Esperado |
|------|----------|
| Seeds M04 idempotentes | `on conflict do nothing` |
| Matriz MODERATOR / ADMIN / SUPERADMIN | Según spec Etapa 3 |
| Roles M03 / AccountType / active_modules | **No** conceden M04 |
| `has_permission` código desconocido | `false` |
| `administrative_audit_log` escritura cliente | Revocada; solo vía RPC |
| `list_administrative_events` | Requiere `audit.view` |
| Funciones privilegiadas | `SECURITY DEFINER` + `search_path = public` + actor `auth.uid()` |

### 1.6 Evidencia / Storage

| Caso | Esperado |
|------|----------|
| Bucket público `leover` | **No** usado |
| Bucket administrativo | **No** creado en Etapa 3 |
| `moderation_evidence_refs` | Solo path lógico; rechazo de URL permanente |

---

## 2. Suite unitaria Android (repo)

| Área | Cobertura |
|------|-----------|
| Matriz M04 | `M04PermissionMatrixTest` |
| Legacy adapter | `ModerationLegacyAccessAdapterTest` (ACTIONED, reporterId null, reason_code) |
| Autorización dominio Etapa 2 | `AdministrativeAuthorizationTest`, `AdministrativeConflictRulesTest` |
| Validadores | Report/Case/Action/Appeal/Verification/Support rules tests |
| Compatibilidad legacy targets | `ModerationLegacyCompatibilityTest` |
| Mocks Etapa 2 | Conservados; DataProvider elige Supabase vs mock |

**Resultado local Etapa 3:** **233** tests, **0** failures, **0** errors.

---

## 3. Adaptación legacy `AdminModerationScreen`

| Antes | Después |
|-------|---------|
| SELECT/UPDATE directo `content_reports` | `list_moderation_queue` / `triage_content_report` / `create_content_report` |
| Gate solo UI | Gate server-side vía permisos M02 en RPC |
| `reporter_id` siempre expuesto | Null sin `moderation.view_sensitive` |

Sin pantallas ni rutas nuevas.

---

## 4. Remoto

**PENDIENTE DE VALIDACIÓN REMOTA** para migraciones `014`–`022`.  
**No se afirma** aplicación ni validación en el proyecto Supabase remoto sin evidencia de deploy + checklist ejecutada.  
**No** aplicar en producción desde esta etapa.
