# M02 — Reporte de validación staging

**Módulo:** M02 — Usuarios, Roles y Permisos  
**Fecha del reporte:** 2026-07-15  
**Entorno objetivo:** Supabase staging (compartido)  
**Estado global:** **PENDIENTE DE VALIDACIÓN REMOTA**

## Motivo

No hubo acceso autorizado a un proyecto Supabase staging desde esta sesión de trabajo.  
No se aplicaron migraciones remotas ni se ejecutaron cuentas técnicas USER/MODERATOR/ADMIN/SUPERADMIN.  
**No se inventan resultados.**

Condición de release: completar este checklist en staging antes de producción.

---

## 0. Precondiciones de aplicación (plan)

| Paso | Acción | Resultado | Evidencia |
|------|--------|-----------|-----------|
| 0.1 | Backup / punto de recuperación staging | No ejecutado | Sin acceso |
| 0.2 | Inspect `supabase_migrations.schema_migrations` | No ejecutado | Sin acceso |
| 0.3 | Aplicar `014` si falta | No ejecutado | Sin acceso |
| 0.4 | Aplicar `015` | No ejecutado | Sin acceso |
| 0.5 | Aplicar `016` | No ejecutado | Sin acceso |
| 0.6 | Aplicar `017` | No ejecutado | Sin acceso |
| 0.7 | Aplicar `018` | No ejecutado | Sin acceso |
| 0.8 | Bootstrap SUPERADMIN (UUID fuera del repo) | No ejecutado | Ver `M02-bootstrap-superadmin.md` |

---

## 1. Perfil y privacidad

| caso | entorno | fecha | actor técnico | resultado | evidencia | observaciones |
|------|---------|-------|---------------|-----------|-----------|---------------|
| Onboarding completo | staging | — | USER | No ejecutado | — | Pendiente acceso |
| Username duplicado | staging | — | USER | No ejecutado | — | Pendiente acceso |
| Username reservado | staging | — | USER | No ejecutado | — | Pendiente acceso |
| Perfil propio completo (SELECT) | staging | — | USER | No ejecutado | — | Pendiente acceso |
| Perfil ajeno solo RPC allowlist | staging | — | USER | No ejecutado | — | Pendiente acceso |
| Privacidad PUBLIC | staging | — | USER | No ejecutado | — | Pendiente acceso |
| Privacidad FRIENDS | staging | — | USER+amigo | No ejecutado | — | Pendiente acceso |
| Privacidad PRIVATE | staging | — | USER | No ejecutado | — | Pendiente acceso |
| Avatar ownership path | staging | — | USER | No ejecutado | — | Pendiente acceso |
| Usuario A no modifica B | staging | — | USER A/B | No ejecutado | — | Pendiente acceso |

---

## 2. Roles y permisos

| caso | entorno | fecha | actor técnico | resultado | evidencia | observaciones |
|------|---------|-------|---------------|-----------|-----------|---------------|
| USER sin moderación | staging | — | USER | No ejecutado | — | Pendiente acceso |
| MODERATOR con moderación | staging | — | MODERATOR | No ejecutado | — | Pendiente acceso |
| ADMIN limitado por jerarquía | staging | — | ADMIN | No ejecutado | — | Pendiente acceso |
| SUPERADMIN completo | staging | — | SUPERADMIN | No ejecutado | — | Pendiente acceso |
| Autoasignación bloqueada | staging | — | ADMIN | No ejecutado | — | Pendiente acceso |
| ADMIN no asigna SUPERADMIN | staging | — | ADMIN | No ejecutado | — | Pendiente acceso |
| Assignment revocado/expirado | staging | — | SUPERADMIN | No ejecutado | — | Pendiente acceso |
| SUSPENDED/BANNED sin permisos elevados | staging | — | cuenta técnica | No ejecutado | — | Pendiente acceso |
| `has_permission` código desconocido → false | staging | — | USER | No ejecutado | — | Pendiente acceso |

---

## 3. Administración

| caso | entorno | fecha | actor técnico | resultado | evidencia | observaciones |
|------|---------|-------|---------------|-----------|-----------|---------------|
| Cambio de estado + historial | staging | — | ADMIN | No ejecutado | — | Pendiente acceso |
| Asignar/revocar roles | staging | — | SUPERADMIN | No ejecutado | — | Pendiente acceso |
| Protección último SUPERADMIN | staging | — | SUPERADMIN | No ejecutado | — | Pendiente acceso |
| Ruta admin profunda denegada a USER | staging | — | USER | No ejecutado | — | Android gate + RPC |
| PII solo con `users.view_private` | staging | — | MODERATOR vs ADMIN | No ejecutado | — | Pendiente acceso |

---

## 4. Validación local (código)

Ejecutada en estación de desarrollo (no remota):

| Control | Resultado |
|---------|-----------|
| Revisión estática migraciones 014–018 | Completada (repo) |
| `assembleDebug` | Ver cierre Etapa 5 |
| `testDebugUnitTest` | Ver cierre Etapa 5 |
| `lintDebug` | Ver cierre Etapa 5 |

---

## 5. Cómo completar este reporte

1. Obtener acceso staging autorizado.  
2. Ejecutar plan 014→018 sin editar migraciones ya aplicadas.  
3. Bootstrap SUPERADMIN técnico.  
4. Replicar cada fila con resultado real (PASS/FAIL), fecha, actor y evidencia (screenshot/SQL log sin secretos).  
5. Actualizar este archivo en un commit posterior de release-prep.
