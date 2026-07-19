# M02 — Pruebas roles, permisos y administración (Etapa 4)

**Módulo:** M02  
**Estado remoto 014–017 / 018:** **No verificado** en staging/producción en esta etapa.

---

## 0. Estado de migraciones (honestidad)

| Migración | En repo | Remoto |
|-----------|---------|--------|
| `014_user_consents.sql` | Sí | **No verificado** (cierre M01: pendiente) |
| `015`–`017` perfil/RLS/storage | Sí | **No verificado** (cierre M02 E3) |
| `018_platform_roles_permissions.sql` | Sí | **No aplicada / no verificada** |

Plan staging sugerido: 014 → 015 → 016 → 017 → 018 → seed bootstrap SUPERADMIN (ver bootstrap doc) → checklist RPC/RLS → Android con 4 cuentas de prueba. **No producción.**

---

## 1. SQL / RLS (manual staging)

| Caso | Esperado |
|------|----------|
| Seed roles/permisos idempotente | Re-ejecutar inserts `on conflict do nothing` OK |
| USER no autoasigna | `assign_platform_role` self → `SELF_ASSIGNMENT_FORBIDDEN` |
| MODERATOR no asigna ADMIN | `HIERARCHY_FORBIDDEN` |
| ADMIN no asigna SUPERADMIN | `HIERARCHY_FORBIDDEN` |
| SUPERADMIN asigna/revoca | OK + fila en `role_assignment_history` |
| Actor = `auth.uid()` | Pasar otro UID no cambia actor |
| Assignment revocado/expirado | `has_permission` = false |
| Cuenta no ACTIVE | `has_permission` = false (RESTRICTED solo perfil) |
| Cambio estado | Historial obligatorio en `user_status_history` |
| UPDATE directo assignments | Denegado (revoke + RLS) |
| Usuario A no lee historial B | Policy / RPC `FORBIDDEN` sin `audit.view` |
| `has_permission` código desconocido | false |
| `search_path` | Fijo `public` en funciones definer |
| Último SUPERADMIN | `LAST_SUPERADMIN_PROTECTED` al revocar |

---

## 2. Android unitarias

| Caso | Suite |
|------|-------|
| USER denegado moderación | `PermissionAndAdminRepositoryTest` |
| MODERATOR permitido | idem |
| Jerarquía ADMIN / SUPERADMIN | idem |
| Autoasignación bloqueada | idem |
| Historial estado | idem |
| Cache invalidate | idem |
| Email privado oculto | idem |
| AuthorizationService | suite Etapa 2 |

---

## 3. Bootstrap SUPERADMIN

Ver `docs/04-calidad/M02-bootstrap-superadmin.md`. **No** versionar UUID/email reales.
