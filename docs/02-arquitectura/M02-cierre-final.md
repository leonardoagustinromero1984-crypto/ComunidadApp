# M02 — Cierre final: Usuarios, Roles y Permisos

**Fecha:** 2026-07-15  
**Módulo:** M02  
**Estado código:** **CERRADO** (nivel implementación + calidad local)  
**Estado release:** **BLOQUEADO** hasta validación staging documentada  
**Rama de cierre:** `m02/etapa-5-validacion-cierre`  

---

## 1. Resumen ejecutivo

M02 entregó perfil social sobre `public.users` (sin segunda tabla profiles), onboarding, privacidad, proyección pública allowlist, Storage con ownership, roles/permisos normalizados server-side, administración mínima y gates deny-by-default en Android.

La validación remota de migraciones **014–018** permanece **pendiente** por falta de acceso staging autorizado en la sesión de cierre.

---

## 2. Entregables por capacidad

### Perfil y onboarding

- Columnas `username`, `display_name`, `onboarding_status`, ubicación, avatar_path.
- Flujo Auth → Legal → Profile → MAIN.
- RPC `complete_profile_onboarding` / `update_my_profile`.

### Privacidad y proyección pública

- `user_privacy_settings` (PUBLIC / FRIENDS / PRIVATE).
- Ajenos solo vía `get_public_user_profile` / `search_public_user_profiles`.
- Sin email, teléfono, modules ni estados internos en proyección pública.

### Storage

- Bucket `profile-avatars`, path `users/{uid}/avatar/{file}`.
- URL firmada temporal; `profile_image_url` legacy como fallback.

### Roles y permisos

- Tablas `platform_roles`, `permissions`, `role_permissions`, `user_role_assignments`.
- RPC `has_permission` (`auth.uid()`, cuenta ACTIVE, deny-by-default).
- Android: `SupabasePermissionRepository` niega ante error; invalidate al logout.
- Sin autoridad por AccountType / `active_modules` / JWT claims.

### Administración

- RPC assign/revoke/status con jerarquía y anti-autoelevación.
- UI mínima protegida por permisos reales.
- Moderación gated por `moderation.view` / `manage_reports`.

### Estados de cuenta

- ACTIVE / RESTRICTED / SUSPENDED / BANNED.
- Cambios solo por RPC; SUSPENDED/BANNED bloquean acceso en sesión.

### Auditoría

- `user_status_history`, `role_assignment_history`.
- Motivos por códigos allowlisted.
- Bootstrap SUPERADMIN documentado sin secretos (`M02-bootstrap-superadmin.md`).

---

## 3. Calidad

| Control | Resultado |
|---------|-----------|
| Unit tests (cierre) | **124** / 0 fallos |
| `assembleDebug` | OK |
| `lintDebug` | OK |
| Suites calidad | `M02-pruebas-perfil-privacidad-rls.md`, `M02-pruebas-roles-permisos-administracion.md` |

---

## 4. Validación remota

Ver `docs/04-calidad/M02-reporte-validacion-staging.md`.

| Ítem | Estado |
|------|--------|
| Aplicación 014–018 en staging | **PENDIENTE** |
| Cuentas técnicas 4 roles | **PENDIENTE** |
| Bootstrap SUPERADMIN | **PENDIENTE** (procedimiento listo) |

**Condición de release:** staging PASS documentado antes de producción.  
**Producción:** no desplegada en M02.

---

## 5. Deuda aceptada

- Staging no ejecutado (bloquea release, no el cierre de código).
- Helpers SQL inmutables menores sin `search_path`.
- FRIENDS en mock Android simplificado vs grafo SQL completo.
- Claims JWT de permisos diferidos (ADR vigente).

---

## 6. Condiciones de release / M03

| Condición | |
|-----------|--|
| Código M02 | Aprobado a nivel repo |
| Staging 014–018 + checklist | Requerido para release |
| Merge a `main` | Decisión de proceso (no hecho en Etapa 5) |
| **M03** | Habilitado solo para **auditoría y diseño** tras aprobar este cierre; **implementación no autorizada** hasta working tree limpio y build/tests/lint verdes en la rama de trabajo de M03 |

---

## 7. Commits de referencia

| Hito | SHA (corto) |
|------|-------------|
| Etapa 3 | `d464cbf` |
| Etapa 4 | `87b8c07` |
| Spec Etapa 5 | `d6604d1` |
| Cierre Etapa 5 / M02 | `b70aad8` |

---

## 8. Checklist cierre M02

- [x] Perfil, privacidad, Storage, roles, admin, historial en código  
- [x] Deny-by-default  
- [x] Calidad local verde  
- [x] Staging pendiente documentado honestamente  
- [x] Release bloqueado sin staging  
- [x] M03 no implementado  
