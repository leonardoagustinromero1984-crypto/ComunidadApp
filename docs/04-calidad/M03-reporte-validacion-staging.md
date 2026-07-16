# M03 — Reporte de validación staging

**Módulo:** M03 — Organizaciones y Equipos  
**Fecha del reporte:** 2026-07-16  
**Entorno objetivo:** Supabase staging (compartido)  
**Estado global:** **PENDIENTE DE VALIDACIÓN REMOTA**  
**Rama:** `m03/etapa-5-validacion-cierre`  
**Commit base Etapa 4:** `11abd6b9a68dd15b1c27e2e0295958275cab7dd1`

## Motivo

No hubo acceso autorizado a un proyecto Supabase staging desde esta sesión de trabajo.  
No se aplicaron migraciones remotas (014–021) ni se ejecutaron cuentas/organizaciones técnicas.  
**No se inventan resultados.**

Condición de release: completar este checklist en staging antes de producción.

---

## 0. Precondiciones de aplicación (plan)

| Paso | Acción | Resultado | Evidencia |
|------|--------|-----------|-----------|
| 0.1 | Backup / punto de recuperación staging | No ejecutado | Sin acceso |
| 0.2 | Inspect `supabase_migrations.schema_migrations` | No ejecutado | Sin acceso |
| 0.3 | Aplicar `014`…`018` si faltan | No ejecutado | Heredado M01/M02 |
| 0.4 | Aplicar `019` (org foundation) | No ejecutado | Sin acceso |
| 0.5 | Aplicar `020` (teams/invites/branches) | No ejecutado | Sin acceso |
| 0.6 | Aplicar `021` (branch RLS privacy fix) | No ejecutado | Sin acceso |

Orden obligatorio cuando exista acceso: `014 → 015 → 016 → 017 → 018 → 019 → 020 → 021`.

---

## 1. Organización y perfil

| caso | entorno | fecha | actor técnico | resultado | evidencia | observaciones |
|------|---------|-------|---------------|-----------|-----------|---------------|
| create_organization + OWNER atómico | staging | — | OWNER | No ejecutado | — | Pendiente acceso |
| Slug duplicado | staging | — | OWNER | No ejecutado | — | Pendiente acceso |
| Tipo OTHER con descripción | staging | — | OWNER | No ejecutado | — | Pendiente acceso |
| Update permitido/denegado | staging | — | OWNER/VIEWER | No ejecutado | — | Pendiente acceso |
| Perfil público allowlist | staging | — | anon/auth | No ejecutado | — | Pendiente acceso |
| Contactos privados por defecto | staging | — | OWNER | No ejecutado | — | Pendiente acceso |
| Contacto público por opt-in | staging | — | OWNER | No ejecutado | — | Pendiente acceso |
| Verificación solo → PENDING | staging | — | OWNER | No ejecutado | — | Pendiente acceso |
| Org SUSPENDED/CLOSED bloqueada | staging | — | OWNER | No ejecutado | — | Pendiente acceso |

---

## 2. Membresías y permisos

| caso | entorno | fecha | actor técnico | resultado | evidencia | observaciones |
|------|---------|-------|---------------|-----------|-----------|---------------|
| Roles OWNER…VIEWER | staging | — | equipo técnico | No ejecutado | — | Pendiente acceso |
| Usuario externo | staging | — | outsider | No ejecutado | — | Pendiente acceso |
| Cuenta M02 SUSPENDED/BANNED | staging | — | blocked | No ejecutado | — | Pendiente acceso |
| AccountType no concede membresía | staging | — | SHELTER legacy | No ejecutado | — | Validado en código local |
| Platform role M02 no concede org | staging | — | SUPERADMIN | No ejecutado | — | Pendiente acceso |
| Error remoto → deny | staging | — | Android | No ejecutado | — | Pendiente acceso |

---

## 3. Invitaciones

| caso | entorno | fecha | actor técnico | resultado | evidencia | observaciones |
|------|---------|-------|---------------|-----------|-----------|---------------|
| Token solo como hash | staging | — | OWNER | No ejecutado | — | Confirmado en SQL repo |
| Aceptación única | staging | — | invitee | No ejecutado | — | Pendiente acceso |
| Expiración | staging | — | invitee | No ejecutado | — | Pendiente acceso |
| Revocación / rechazo | staging | — | OWNER/invitee | No ejecutado | — | Pendiente acceso |
| No invitar OWNER | staging | — | OWNER | No ejecutado | — | Constraint + RPC |
| Sin membresía antes de accept | staging | — | invitee | No ejecutado | — | Pendiente acceso |
| Token inválido genérico | staging | — | outsider | No ejecutado | — | Pendiente acceso |

---

## 4. Ownership y cierre

| caso | entorno | fecha | actor técnico | resultado | evidencia | observaciones |
|------|---------|-------|---------------|-----------|-----------|---------------|
| Último OWNER protegido | staging | — | OWNER | No ejecutado | — | Confirmado en SQL/Android |
| ADMIN no se autoeleva | staging | — | ADMIN | No ejecutado | — | Pendiente acceso |
| Transferencia atómica | staging | — | OWNER | No ejecutado | — | Confirmado en SQL repo |
| Cierre revoca invitaciones | staging | — | OWNER | No ejecutado | — | Pendiente acceso |

---

## 5. Sucursales y Storage

| caso | entorno | fecha | actor técnico | resultado | evidencia | observaciones |
|------|---------|-------|---------------|-----------|-----------|---------------|
| CRUD sucursal sin coordenadas | staging | — | MANAGER | No ejecutado | — | Pendiente acceso |
| Contacto privado por defecto | staging | — | MANAGER | No ejecutado | — | Pendiente acceso |
| Perfil público allowlist | staging | — | anon | No ejecutado | — | RPC + fix 021 |
| Org A no modifica sucursal B | staging | — | OWNER A/B | No ejecutado | — | Pendiente acceso |
| Logo/cover ownership Storage | staging | — | OWNER | No ejecutado | — | Pendiente acceso |
| No miembro no sube/elimina | staging | — | outsider | No ejecutado | — | Pendiente acceso |

---

## 6. Android

| caso | entorno | fecha | actor técnico | resultado | evidencia | observaciones |
|------|---------|-------|---------------|-----------|-----------|---------------|
| Selector personal/organización | dispositivo | — | usuario | No ejecutado | — | Pendiente staging app |
| Cambio contexto refresca permisos | dispositivo | — | usuario | No ejecutado | — | Código: OrganizationContextProvider |
| Logout limpia contexto | local unit | 2026-07-16 | — | PASS código | SessionViewModel | Unit + review |
| Ruta directa sin permiso | dispositivo | — | usuario | No ejecutado | — | Pendiente |

---

## 7. Validación local (código)

Ejecutada en estación de desarrollo (no remota):

| Control | Resultado |
|---------|-----------|
| `assembleDebug` | SUCCESS |
| `testDebugUnitTest` | **172** tests, **0** failures, **0** errors |
| `lintDebug` | SUCCESS |
| Migraciones en repo | 014–021 presentes; 014–020 no editadas; 021 correctiva nueva |

---

## 8. Conclusión

```text
PENDIENTE DE VALIDACIÓN REMOTA
```

Release de M03 **bloqueado** hasta staging PASS documentado.  
Cierre de código M03 **habilitado** con deuda remota explícita.
