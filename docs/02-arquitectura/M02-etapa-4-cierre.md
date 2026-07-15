# M02 — Cierre Etapa 4: Roles, permisos y administración segura

**Fecha:** 2026-07-15  
**Rama:** `m02/etapa-4-roles-permisos-administracion`  
**Módulo:** M02 — Usuarios, Roles y Permisos  
**Estado de entrada:** Etapa 3 aprobada (`d464cbf`)  
**Spec base:** `33955a5` — docs Etapa 4 aprobada  

---

## 1. Rama y commits

| Ref | SHA / nota |
|-----|------------|
| Etapa 3 consolidada | `d464cbf` — `feat: complete M02 stage 3 profile onboarding privacy and RLS` |
| Spec Etapa 4 | `33955a5` |
| Base trabajo implementación | `33955a5` |
| WIP GPS/mapas/pagos | **No** incorporado |

---

## 2. Estado migraciones 014–017 (+018)

| Migración | Repo | Remoto |
|-----------|------|--------|
| `014_user_consents.sql` | Sí | **No verificado** (M01: pendiente) |
| `015`–`017` | Sí | **No verificado** (M02 E3) |
| `018_platform_roles_permissions.sql` | Sí (nueva) | **No aplicada / no verificada** |

No se editaron migraciones ya versionadas `014`–`017`.  
Plan staging: 014→015→016→017→018 → bootstrap SUPERADMIN → checklist. **Sin producción.**

---

## 3. SQL Etapa 4 (`018`)

### Tablas

- `platform_roles`, `permissions`, `role_permissions`
- `user_role_assignments`
- `user_status_history`, `role_assignment_history`

### Seeds

Roles: USER, MODERATOR, ADMIN, SUPERADMIN  
Permisos: profile.*, moderation.*, users.*, roles.*, audit.view  

### RPCs (actor = `auth.uid()`)

- `has_permission`, `get_my_permissions`, `get_my_platform_roles`
- `assign_platform_role`, `revoke_platform_role`
- `change_user_account_status`
- `admin_search_users`, `admin_get_user_roles`, `admin_get_user_status_history`
- `ensure_my_default_user_role`

### Controles

- Deny-by-default; cuenta no ACTIVE sin permisos elevados
- Sin autoasignación / autoelevación / cambio de estado propio
- Jerarquía ADMIN/SUPERADMIN
- Protección último SUPERADMIN
- Historial obligatorio con reason codes allowlisted
- Sin INSERT/UPDATE/DELETE cliente en tablas de roles/historial

Bootstrap: `docs/04-calidad/M02-bootstrap-superadmin.md` (UUID fuera del repo).

---

## 4. Android

| Pieza | Cambio |
|-------|--------|
| `SupabasePermissionRepository` | RPCs; cache TTL; **niega ante error**; invalidate logout |
| `PlatformAdministrationRepository` | Admin separados de `UserRepository` |
| Moderación | `moderation.view` / `manage_reports` reales |
| UI admin mínima | Búsqueda, detalle, estado, roles, historial + confirmación |
| Gates nav | Botones/rutas moderación y administración por permiso |

**No** usa AccountType, `active_modules` ni claims JWT como autoridad.

---

## 5. Pruebas

| Control | Resultado |
|---------|-----------|
| Docs SQL | `docs/04-calidad/M02-pruebas-roles-permisos-administracion.md` |
| Unitarias | **121** tests, **0** failures |
| `assembleDebug` | OK |
| `lintDebug` | OK |

### Remoto

**No verificado.** No se afirma aplicación de 014–018 ni checklist staging.

---

## 6. Fuera de alcance (cumplido)

- Sin M03 / orgs
- Sin GPS, mapas, pagos
- Sin Hilt/Retrofit/NestJS
- Sin Etapa 5 / cierre final M02
- Sin deploy producción

---

## 7. Deuda Etapa 5

- Aplicar y validar 014–018 en staging
- Bootstrap SUPERADMIN auditado
- Cuentas de prueba USER/MODERATOR/ADMIN/SUPERADMIN
- Calidad final y cierre M02

---

## 8. Checklist

- [x] Etapa 3 consolidada
- [x] Rama limpia sin WIP
- [x] Estado 014–017 documentado
- [x] Roles/permisos normalizados + seeds
- [x] Sin autoridad AccountType/modules
- [x] `has_permission` server-side
- [x] Actor `auth.uid()`, sin autoelevación
- [x] Historial obligatorio
- [x] PermissionRepository niega ante error
- [x] Moderación/admin con permisos reales
- [x] Sin M03
- [x] Tests / assemble / lint OK
- [x] Remoto declarado honestamente
- [x] Este cierre creado

---

## 9. Parada

**No** se inicia Etapa 5 ni M03.
