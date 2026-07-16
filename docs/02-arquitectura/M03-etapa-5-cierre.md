# M03 — Cierre Etapa 5: Validación staging, calidad y cierre

**Fecha:** 2026-07-16  
**Rama:** `m03/etapa-5-validacion-cierre`  
**Módulo:** M03 — Organizaciones y Equipos  
**Estado de entrada:** Etapa 4 aprobada (`11abd6b9a68dd15b1c27e2e0295958275cab7dd1`)  
**Spec Etapa 5:** `docs/03-modulos/M03-Etapa-5-Validacion-Staging-Calidad-y-Cierre-Final.md`

---

## 1. Estado Git

| Ref | Valor |
|-----|--------|
| Commit base | `11abd6b9a68dd15b1c27e2e0295958275cab7dd1` |
| Rama inicial | `m03/etapa-4-equipos-invitaciones-sucursales` (limpia salvo spec Etapa 5 untracked) |
| Rama trabajo | `m03/etapa-5-validacion-cierre` |
| WIP GPS/mapas/pagos | **No** incorporado |
| Merge a `main` | **No** |
| M04 | **No** iniciado |

---

## 2. Archivos

### Creados

| Archivo |
|---------|
| `supabase/migrations/021_organizations_branches_rls_privacy_fix.sql` |
| `docs/02-arquitectura/M03-etapa-5-cierre.md` (este) |
| `docs/02-arquitectura/M03-cierre-final.md` |
| `docs/04-calidad/M03-reporte-validacion-staging.md` |
| `docs/03-modulos/M03-Etapa-5-Validacion-Staging-Calidad-y-Cierre-Final.md` (spec aprobada) |

### Modificados / eliminados

Ninguno de código Android (solo documentación + migración correctiva).

---

## 3. Revisión estática 014–020 (+021)

| Migración | Rol | Editada en Etapa 5 |
|-----------|-----|--------------------|
| 014–018 | M01/M02 foundation | **No** |
| 019 | Org foundation, RLS, Storage | **No** |
| 020 | Invitaciones, equipos, sucursales | **No** |
| 021 | Fix RLS branches (privacidad teléfono) | **Nueva correctiva** |

### Confirmaciones de seguridad (repo)

| Control | Evidencia |
|---------|-----------|
| Tokens solo `token_hash` | 020: columna + `org_hash_invitation_token` |
| Deny-by-default | `has_org_permission` / Android deny ante error |
| Último OWNER | `leave_organization`, `_org_can_admin_target_member` |
| Transferencia atómica | `transfer_organization_ownership` (una transacción plpgsql) |
| Contactos privados / público allowlist | defaults false; `organization_public_json` / `org_branch_public_json` |
| Storage ownership | bucket `organization-media` + `has_org_permission(...update)` |
| OrganizationContext en logout | `SessionViewModel.logout` → `clear()` |
| AccountType / active_modules | no otorgan membresía; `grantsFromAccountType…=false` |
| Legacy shelters/services | resource links opcionales; `PublishViewModel.shelterId=null` |
| SECURITY DEFINER + `search_path=public` | 019/020 RPCs auditadas |
| Escritura directa sensible | INSERT/UPDATE/DELETE revoked en tablas org |

### Corrección realizada

**021:** la política `organization_branches_select` permitía a cualquier `authenticated` leer filas ACTIVE (incluido `contact_phone` privado). Se restringió a `organization.view_private`. La proyección pública sigue solo por RPC allowlist.

---

## 4. Comandos ejecutados

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline
```

| Control | Resultado |
|---------|-----------|
| `assembleDebug` | **SUCCESS** |
| `testDebugUnitTest` | **172** tests, **0** failures, **0** errors |
| `lintDebug` | **SUCCESS** |

No se eliminaron tests. No se agregó suppress/baseline global.

---

## 5. Staging

```text
PENDIENTE DE VALIDACIÓN REMOTA
```

Detalle: `docs/04-calidad/M03-reporte-validacion-staging.md`.  
Release bloqueado hasta staging PASS. Cierre de código habilitado.

---

## 6. Riesgos / deuda aceptada

| Riesgo | Severidad | Nota |
|--------|-----------|------|
| Staging 014–021 no aplicado | Release | Bloquea producción |
| Paths públicos logo/cover vs Storage member-only | Media | URLs firmadas en Android; follow-up posible |
| Transferencia sin `FOR UPDATE` | Baja | Atómica por llamada; carrera concurrente rara |

---

## 7. Checklist Etapa 5

- [x] Commit base verificado  
- [x] Rama limpia sin WIP  
- [x] Migraciones 014–020 revisadas (sin editar aplicadas)  
- [x] Corrección 021 documentada  
- [x] Tokens solo hash  
- [x] Deny-by-default  
- [x] Último OWNER / transferencia atómica  
- [x] Contactos privados / allowlist  
- [x] Storage ownership  
- [x] Context clear en logout  
- [x] AccountType sin autoridad  
- [x] Legacy shelters/services no rotos (código)  
- [x] Build / tests / lint verdes  
- [x] Staging marcado honestamente  
- [x] Sin M04 / sin merge `main`  
- [x] Tres documentos de salida  

---

## 8. Parada

**No** merge a `main`.  
**No** iniciar implementación M04.

M04 queda habilitado solo para **auditoría y diseño** tras aprobar `M03-cierre-final.md`.
