# LEOVER — M04 Auditoría inicial (Administración, Moderación y Soporte)

**Módulo:** M04 — Administración, Moderación y Soporte  
**Etapa:** 1 — Auditoría y diseño  
**Fecha:** 2026-07-16  
**Rama:** `m04/administracion-moderacion-soporte-auditoria`  
**Dependencia:** M03 cerrado a nivel código y calidad local (`M03-cierre-final.md`)  
**Backend oficial:** Supabase (ADR-0001)  
**Alcance:** inventario y diseño; **sin** migraciones, **sin** cambios de RLS, **sin** tablas/repositorios/pantallas nuevas, **sin** sanciones reales, **sin** M05  

**Documentos de entrada (orden leído):**

1. [`docs/01-producto/D01-Modulos-y-Orden.md`](../01-producto/D01-Modulos-y-Orden.md)  
2. [`docs/02-arquitectura/M00-cierre-final.md`](M00-cierre-final.md)  
3. [`docs/02-arquitectura/M01-cierre-final.md`](M01-cierre-final.md)  
4. [`docs/02-arquitectura/M02-cierre-final.md`](M02-cierre-final.md)  
5. [`docs/02-arquitectura/M03-cierre-final.md`](M03-cierre-final.md)  
6. [`docs/03-modulos/M02-Usuarios-Roles-y-Permisos.md`](../03-modulos/M02-Usuarios-Roles-y-Permisos.md)  
7. [`docs/03-modulos/M03-Organizaciones-y-Equipos.md`](../03-modulos/M03-Organizaciones-y-Equipos.md)  
8. ADR-0001 … ADR-0005  
9. [`docs/03-modulos/M04-Administracion-Moderacion-y-Soporte.md`](../03-modulos/M04-Administracion-Moderacion-y-Soporte.md)  

---

## 0. Estado Git y calidad

| Ref | Nota |
|-----|------|
| Tip M03 cierre | `e73e8fa3bd1700065612ed2b9a8c03702e104841` |
| Spec M04 | untracked / presente en working tree al crear rama |
| Rama | `m04/administracion-moderacion-soporte-auditoria` |
| WIP GPS/mapas/pagos | **No** mezclado |
| Merge a `main` | **No** |
| Stash preservado | `stash@{0}: backup/pre-m01-identidad-autenticacion` |

| Control | Resultado |
|---------|-----------|
| `assembleDebug` | SUCCESS |
| `testDebugUnitTest` | **172** tests, **0** failures, **0** errors |
| `lintDebug` | SUCCESS |

**Remoto / staging 014–021:** **PENDIENTE DE VALIDACIÓN REMOTA** (bloquea release; no bloquea esta auditoría). No se afirma aplicación remota.

---

## 1. Hallazgo central

Existe una **base administrativa parcial de M02** (usuarios, roles, estados, historial de estado) y un **prototipo legacy de reportes** (`content_reports` + `AdminModerationScreen`), pero **no** existe el producto M04:

- sin casos de moderación;
- sin medidas tipadas con evidencia;
- sin apelaciones;
- sin cola de verificación organizacional (solo solicitud PENDING en M03);
- sin tickets de soporte;
- sin Storage de evidencia administrativa;
- sin notas internas operativas.

Además, el prototipo de reportes tiene un **fallo crítico de RLS**: cualquier usuario autenticado puede SELECT/UPDATE todas las filas de `content_reports`.

---

## 2. Inventario SQL (migraciones 001–021)

### 2.1 Reportes y bloques

| Recurso | Origen | RLS vigente (repo) | Notas |
|---------|--------|--------------------|-------|
| `user_blocks` | `012` | `blocker_id = auth.uid()` (ALL) | Bloqueo social usuario↔usuario; **no** es sanción de plataforma |
| `content_reports` | `012` | SELECT `using (true)`; UPDATE `using (true)`; INSERT reporter = auth.uid() | Target: POST\|USER\|COMMENT; status OPEN\|REVIEWED\|DISMISSED\|ACTIONED |
| Endurecimiento posterior | — | **Ninguno** en 013–021 | Riesgo crítico abierto |

### 2.2 Administración de usuarios / permisos (M02)

| Recurso | Origen | Uso M04 |
|---------|--------|---------|
| `platform_roles` / `permissions` / `role_permissions` / `user_role_assignments` | `018` | Reutilizar |
| `user_status_history` | `018` | Reutilizar (no duplicar columnas de estado) |
| `role_assignment_history` | `018` | Escrito por RPC; **sin** lectura admin en Android |
| RPCs `admin_*`, `assign_platform_role`, `revoke_platform_role`, `change_user_account_status`, `has_permission` | `018` | Reutilizar como fuente de verdad |

Permisos seeded relevantes:

```text
moderation.view
moderation.manage_reports
users.view_private
users.change_status
roles.view / assign / revoke
audit.view
```

**No seeded (candidatos M04):**  
`moderation.manage_cases`, `moderation.apply_actions`, `moderation.view_sensitive`, `moderation.review_appeals`,  
`organizations.review_verification`, `organizations.revoke_verification`,  
`support.view`, `support.manage`, `support.view_sensitive`.

### 2.3 Organización / verificación (M03)

| Recurso | Origen | Estado |
|---------|--------|--------|
| `organizations.verification_status` | `019` | Contrato listo |
| `request_organization_verification` | `019` | Solo → PENDING; nunca VERIFIED desde cliente |
| `organization_status_history` / `organization_audit_log` | `019`/`020` | Historial org; no cola M04 |
| Docs / reviews de verificación | — | **Ausentes** |
| `021` | Fix RLS branches | Irrelevante a reportes |

### 2.4 Storage

| Bucket | Origen | ¿Evidencia M04? |
|--------|--------|-----------------|
| `leover` (público) | `002` | **No** — inseguro para evidencia |
| `profile-avatars` | `017` | No |
| `organization-media` | `019` | Media org; no evidencia de caso |

No hay bucket de evidencia administrativa ni políticas de retención.

### 2.5 Otros

- `lost_found_sightings` / notificaciones / chat: no son colas M04.
- Búsqueda en repo de `appeal`, `support_ticket`, `moderation_case`, `internal_note` (admin): **sin matches** de dominio M04.

---

## 3. Inventario Android

### 3.1 Pantallas y rutas

| Pieza | Path | Rol actual |
|-------|------|------------|
| `AdminModerationScreen` | `ui/screens/admin/` | Lista OPEN; Desestimar / Tomar acción (solo status) |
| `AdminModerationViewModel` | `viewmodel/` | Gate `moderation.view`; mutación exige `moderation.manage_reports` |
| `PlatformAdminScreen` | `ui/screens/admin/` | Búsqueda usuarios, roles, status, historial status |
| `PlatformAdminViewModel` | `viewmodel/` | Acceso si `roles.view` **o** `users.change_status` **o** `moderation.view` |
| Profile entry | `ProfileScreen` | Botones Moderación / Administración gated |
| Rutas | `NavRoutes.ADMIN_MODERATION`, `PLATFORM_ADMIN` | Siempre registradas; pantallas se auto-niegan |

**Ausentes:** cola verificación org, apelaciones, soporte, detalle de caso, evidencia, asignación.

### 3.2 Repositorios

| Repo | Estado | Reutilizar en M04 |
|------|--------|-------------------|
| `PermissionRepository` / `AuthorizationService` | M02 deny-by-default | **Sí** |
| `PlatformAdministrationRepository` (+ Mock/Supabase) | Admin usuarios vía RPC | **Sí** (no mezclar casos aquí) |
| `PlatformRepository` (+ `PlatformSupabaseDataSource`) | Reportes/bloques + social | Reportes usuario↔contenido; **no** como cocina staff completa |
| Org repos M03 | Verificación request | Extender con **review** en repo dedicado futuro |

### 3.3 Modelos

- `ContentReport` / `ReportTargetType` / `ReportStatus` en `PlatformModels.kt` — mínimos vs catálogo M04.
- Sin `domain/moderation`, `domain/support`, `domain/verification` (review).

---

## 4. Autoridad y gates (confirmado)

| Afirmación | Evidencia |
|------------|-----------|
| Roles internos M03 **no** otorgan moderación global | `OrganizationPermissionCode` solo `organization.*`; sin bridge a `moderation.*` |
| AccountType / active_modules **no** son autoridad admin | `AuthorizationService.grantsFromAccountTypeOrModules() = false`; `ModulePermissions.canModerateContent = false` |
| Deny-by-default plataforma | `has_permission` + Android niega ante error / contexto vacío |
| UI no es seguridad final | Criticado: RLS de `content_reports` abre el API |

**Inconsistencia menor:** el botón Profile “Administración” no incluye `moderation.view`, pero `PlatformAdminViewModel` / RPC search sí permiten MODERATOR vía deep link.

---

## 5. Gaps vs diseño M04

| Requisito M04 | Estado actual |
|---------------|---------------|
| Reportes tipados ricos + prioridades + estados extendidos | Solo POST/USER/COMMENT + 4 statuses |
| Casos que agrupan reportes | Ausente |
| Medidas proporcionales (hide/restrict/suspend/org…) | Status de cuenta M02 existe; no ligado a cola de reportes; “ACTIONED” cosmético |
| Apelaciones | Ausente (solo reason code `appeal_accepted` en historial) |
| Verificación org (revisión/docs) | Solo solicitud PENDING |
| Soporte / tickets | Ausente (“contactá soporte” copy) |
| Colas / asignación | Ausente |
| Evidencia + Storage privado | Ausente |
| Notas internas | Columna `note` en historiales SQL; no capturada en UI admin |
| Rate limit / abuso de reportes | Ausente |
| Permisos M04 extendidos | No seed |

---

## 6. Duplicaciones y reutilización

### Reutilizar tal cual

- M01 sesión / `auth.uid()`  
- M02 `PermissionRepository`, `has_permission`, account status, `user_status_history`  
- `PlatformAdministrationRepository` + RPCs 018  
- Patrones UI PlatformAdmin (confirmación, PII gated por `users.view_private`)  
- M03 `verification_status` + request (lado review a construir)  
- `AppResult` / `AppError` / `AppLogger` / providers (ADR-0003)

### Reutilizar con adaptación (no sustituyen M04)

- `content_reports` / `AdminModerationScreen` — prototipo; **endurecer o reemplazar** antes de construir producto  
- `user_blocks` — social, no sanción plataforma  
- Historiales org M03 — auditoría org, no cola staff global  

### No reutilizar como autoridad

- AccountType / active_modules  
- OrganizationRoleCode  
- RLS “SELECT/UPDATE true” de `content_reports`  
- Bucket público `leover` para evidencia  

---

## 7. Riesgos de seguridad / privacidad

| ID | Riesgo | Severidad | Mitigación propuesta (etapas futuras) |
|----|--------|-----------|----------------------------------------|
| R1 | `content_reports` SELECT/UPDATE abierto a authenticated | **Crítica** | RPC + revoke grants; o políticas por `has_permission` |
| R2 | UI deny-by-default pero API reports fail-open | Alta | Misma que R1 |
| R3 | “Tomar acción” no aplica medida real | Media (producto/confianza) | Medidas tipadas + historial |
| R4 | Storage público inadecuado para evidencia | Alta | Bucket privado + signed URLs (alinear M05) |
| R5 | Deep links admin sin RLS staff | Media | Gates VM + endurecer API |
| R6 | Staging 014–021 pendiente | Release | Heredado; no inventar remoto |
| R7 | PII de reporter en filas abiertas | Alta | Consecuencia de R1 |

---

## 8. Propuesta de modelo (solo diseño — **no implementar en Etapa 1**)

Tablas candidatas (confirmar en Etapa 2–3; no crear todas a ciegas):

```text
moderation_reports          -- evolucionar o reemplazar content_reports
moderation_cases
moderation_case_reports
moderation_evidence
moderation_case_notes
moderation_actions
moderation_appeals
support_tickets
support_ticket_messages
organization_verification_documents
organization_verification_reviews
administrative_assignments
administrative_audit_log
```

**Decisiones recomendadas:**

1. **Endurecer reportes primero** (RLS/RPC) antes de ampliar UI.  
2. **No** mezclar colas staff dentro de `UserRepository` / `OrganizationRepository`.  
3. Reutilizar estados de cuenta M02 y estados org M03; M04 aplica medidas, no duplica columnas.  
4. Verificación: M03 solicita; M04 revisa (nunca auto-VERIFIED).  
5. Evidencia: contrato en M04; infra de archivos alineada a M05.  
6. Ampliar permisos M02 solo si la matriz MODERATOR/ADMIN/SUPERADMIN no alcanza.  
7. Roles internos M03 siguen sin autoridad M04.

---

## 9. Archivos a crear/modificar (plan futuro; **no en Etapa 1**)

### Etapa 2 — Contratos

- `domain/moderation/*`, `domain/support/*`, `domain/verification/*` (review)  
- Validadores / jerarquía de medidas / anti-conflicto de apelación  
- Tests unitarios  

### Etapa 3 — Persistencia

- Migración siguiente a `021` (**no editar** 012/018/019 aplicadas)  
- RPC + RLS deny-by-default  
- Seeds de permisos nuevos si se aprueban  
- Tests SQL documentados  

### Etapa 4 — UI operativa

- Bandejas, detalle, acciones, apelaciones, verificación, soporte  
- Repos dedicados Supabase  

### Etapa 5 — Calidad / staging

- Checklist remoto  
- Cierre M04  

---

## 10. Decisiones que requieren aprobación

1. ¿Evolucionar `content_reports` in-place o crear `moderation_reports` y migrar?  
2. ¿Qué permisos M04 nuevos se seedearán vs ampliar solo MODERATOR/ADMIN?  
3. ¿La cola de verificación org es el primer entregable staff post-hardening?  
4. ¿Evidencia espera M05 o bucket mínimo en M04?  
5. ¿Soporte M04 es solo tickets internos (sin chat realtime)?  
6. ¿Política de retención/borrado de evidencia y reportes?  
7. ¿Quién puede ver `reporter_id` / detalles (`moderation.view_sensitive`)?  

---

## 11. Plan por etapas (resumen)

| Etapa | Entrega | Bloqueos |
|-------|---------|----------|
| **1 (esta)** | Auditoría | — |
| 2 | Dominio + authz (sin SQL) | Aprobación decisiones §10 |
| 3 | SQL/RLS/RPC/colas | Preferible staging 014–021 |
| 4 | UI operativa | — |
| 5 | Staging M04 + cierre | Sin producción |

---

## 12. Checklist Etapa 1

- [x] Inventario AdminModeration / PlatformAdmin / PlatformRepository  
- [x] Inventario reports/blocks/account status/historiales  
- [x] Permisos M02 de moderación documentados  
- [x] Verificación M03 (request-only) documentada  
- [x] Storage / evidencia / PII / notas / apelaciones / soporte  
- [x] Roles M03 sin autoridad global confirmado  
- [x] AccountType / active_modules sin autoridad confirmado  
- [x] Propuesta de modelo sin implementar  
- [x] Build / tests / lint ejecutados  
- [x] Staging remoto declarado pendiente  
- [x] Sin migraciones / RLS / pantallas / sanciones / M05  

---

## 13. Parada

**No** se inicia Etapa 2 ni M05.  
**No** merge a `main`.  
**No** se modificó funcionalidad (solo este documento + preservación de la spec M04 en working tree).

Entregable: este documento.
