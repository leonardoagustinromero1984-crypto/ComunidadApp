# M03 — Cierre Etapa 3: Persistencia, RLS, perfil y Storage

**Fecha:** 2026-07-15  
**Rama:** `m03/etapa-3-persistencia-perfil-rls`  
**Módulo:** M03 — Organizaciones y Equipos  
**Estado de entrada:** Etapa 2 aprobada (`1789a9ef4c805835530946b6483c092d3df3108d`)  
**Spec Etapa 3:** `e3e11c3` — `docs: add approved M03 stage 3 persistence RLS profile and storage spec`  

---

## 1. Rama y commits

| Ref | SHA / nota |
|-----|------------|
| Commit base Etapa 2 | `1789a9ef4c805835530946b6483c092d3df3108d` |
| Spec Etapa 3 | `e3e11c3` |
| WIP GPS/mapas/pagos | **No** incorporado |
| Merge a `main` | **No** |
| M04 | **No** iniciado |

---

## 2. Migración

| Ítem | Valor |
|------|-------|
| Archivo | `supabase/migrations/019_organizations_foundation.sql` |
| Numeración | Siguiente a `018` (014–018 presentes en repo) |
| Edición de 014–018 | **No** |
| Remoto / staging | **PENDIENTE DE VALIDACIÓN REMOTA** |

### Tablas

- `organizations` (entidad nueva)
- `organization_memberships`
- `organization_roles` / `organization_permissions` / `organization_role_permissions`
- `organization_status_history` / `organization_audit_log`
- `organization_resource_links` (`SHELTER_LISTING`, `SERVICE_PROFILE`)

### Seeds

Roles internos OWNER…VIEWER y permisos `organization.*` (matriz alineada a Android). Separados de `platform_roles` M02.

### RPCs (actor `auth.uid()`, SECURITY DEFINER, `search_path = public`)

- `create_organization` — transaccional: DRAFT + membresía OWNER ACTIVE + audit
- `update_my_organization`
- `get_my_organizations`
- `get_public_organization_by_slug` / `search_public_organizations` (allowlist)
- `has_org_permission` / `get_my_org_permissions` (deny-by-default)
- `request_organization_verification` (solo → PENDING; nunca VERIFIED desde cliente)
- `link_organization_resource` / `unlink_organization_resource`

### RLS

- SELECT membresía / helpers `SECURITY DEFINER` (anti-recursión)
- Sin INSERT/UPDATE/DELETE directo `authenticated` en tablas org (escritura vía RPC)

### Storage

- Bucket privado `organization-media`
- Paths: `organizations/{orgId}/logo|cover/{file}`
- Upload/update/delete con `organization.update`
- Paths persistidos; URLs firmadas temporales en Android

---

## 3. Android

| Pieza | Entrega |
|-------|---------|
| Repos | Interfaces + mocks; `SupabaseOrganizationRepository` / Membership / Permission |
| Invitaciones | Mock (Etapa 4) |
| Storage | `OrganizationMediaStorageService` + `StoragePaths` |
| DataProvider | Mock o Supabase según flag; media solo con Supabase |
| UI mínima | Mis organizaciones, crear, editar, perfil público por slug |
| Entrada | Profile → “Mis organizaciones” (**sin** AccountType) |
| Gates | `hasOrgPermission`; deny ante loading/error |

---

## 4. Decisiones respetadas

- `organizations` nueva; shelters/services legacy vinculables
- Sin auto-creación desde AccountType
- FOSTER_HOME capacidad personal
- OWNER inicial atómico con create
- Roles internos ≠ M02
- Contactos privados por defecto; público allowlist
- Deny-by-default servidor y Android
- Sin invitaciones reales / equipos completos / transferencia OWNER / sucursales

---

## 5. Pruebas y calidad

| Control | Resultado |
|---------|-----------|
| Suite SQL documentada | `docs/04-calidad/M03-pruebas-persistencia-rls-organizaciones.md` |
| Unit tests dominio/mock org | Incluidos en `testDebugUnitTest` |
| `assembleDebug` | **SUCCESS** (BUILD SUCCESSFUL) |
| `testDebugUnitTest` | **166 tests, 0 failures, 0 errors** |
| `lintDebug` | **SUCCESS** (BUILD SUCCESSFUL) |
---

## 6. Remoto

**PENDIENTE DE VALIDACIÓN REMOTA** para migraciones **014–019**.  
No se afirma deploy ni checklist staging/producción.

Release sigue bloqueado hasta staging documentado.

---

## 7. Fuera de alcance (respetado)

Invitaciones reales, gestión completa de miembros, transferencia OWNER, sucursales, verificación documental (M04), GPS/mapas/pagos, Hilt/Retrofit, merge `main`, producción.

---

## 8. Checklist Etapa 3

- [x] Commit base verificado  
- [x] Rama `m03/etapa-3-persistencia-perfil-rls`  
- [x] Migración `019` sin editar aplicadas  
- [x] `organizations` entidad nueva  
- [x] OWNER inicial transaccional  
- [x] Roles internos separados de M02  
- [x] `has_org_permission` deny-by-default  
- [x] Perfil público allowlist  
- [x] Contactos privados por defecto  
- [x] Storage privado con ownership  
- [x] AccountType no concede permiso  
- [x] Resource links sin migración automática  
- [x] Verificación solo solicitud PENDING  
- [x] UI mínima  
- [x] Invitaciones/equipos completos no iniciados  
- [x] Remoto declarado honestamente  
- [x] Sin M04  
- [x] Calidad Gradle full confirmada en esta sesion
- [x] Cierre creado  

---

## 9. Parada

**No** se inicia Etapa 4 ni M04.  
**No** merge a `main`.

Siguiente habilitado solo tras aprobación y calidad local verde: **M03 Etapa 4**.
