# M03 — Pruebas SQL/RLS: equipos, invitaciones y sucursales

**Módulo:** M03 — Organizaciones y Equipos  
**Etapa:** 4  
**Migración:** `020_organizations_teams_invitations_branches.sql`  
**Dependencias:** `019_organizations_foundation.sql` (+ 014–018 en repo)  
**Estado remoto:** **PENDIENTE DE VALIDACIÓN REMOTA**

Este documento define casos de prueba SQL/RLS a ejecutar en staging cuando exista acceso autorizado. No afirma resultados remotos.

---

## 1. Precondiciones

1. Aplicar migraciones 014–020 en un proyecto staging (no producción).
2. Cuentas de prueba:
   - `owner_a` (ACTIVE)
   - `admin_b` (ACTIVE, miembro ADMIN)
   - `member_c` (ACTIVE, MEMBER)
   - `viewer_d` (ACTIVE, VIEWER)
   - `outsider_e` (ACTIVE, sin membresía)
   - `blocked_f` (SUSPENDED o BANNED)
3. Organización de prueba creada vía `create_organization` por `owner_a`.

---

## 2. Invitaciones

| ID | Caso | Expectativa |
|----|------|-------------|
| I01 | `invite_organization_member` con rol MEMBER | Inserta fila PENDING; `token_hash` presente; token plano solo en respuesta RPC |
| I02 | Invitar rol OWNER | Rechazo (`ROLE_NOT_INVITABLE`) |
| I03 | Token no se guarda en claro | Columna `token_hash` ≠ token raw; no hay columna token |
| I04 | Usuario externo lista invitaciones | `FORBIDDEN` / sin filas |
| I05 | Destino acepta token válido | Membresía ACTIVE creada; invitación ACCEPTED; un solo uso |
| I06 | Doble aceptación | Segunda llamada falla o es idempotente sin duplicar ACTIVE |
| I07 | Token expirado | No crea membresía; estado EXPIRED |
| I08 | Invitación revocada | `accept` falla; sin membresía |
| I09 | Destino incorrecto | `INVITATION_INVALID` genérico (sin enumeración) |
| I10 | Decline | Estado DECLINED; sin membresía |

---

## 3. Miembros y jerarquía

| ID | Caso | Expectativa |
|----|------|-------------|
| M01 | OWNER cambia rol MEMBER→MANAGER | OK + audit |
| M02 | ADMIN asigna OWNER | `USE_TRANSFER_OWNERSHIP` / FORBIDDEN |
| M03 | ADMIN remueve OWNER | FORBIDDEN |
| M04 | Remover último OWNER | `LAST_OWNER_PROTECTED` / FORBIDDEN |
| M05 | Suspender MEMBER | status SUSPENDED; `has_org_permission` false |
| M06 | Leave último OWNER | `LAST_OWNER_PROTECTED` |
| M07 | Leave MEMBER | status LEFT; sin permisos |

---

## 4. Transferencia de ownership

| ID | Caso | Expectativa |
|----|------|-------------|
| T01 | OWNER → MEMBER activo | Destino OWNER; actor ADMIN; ≥1 OWNER |
| T02 | No-OWNER intenta transfer | FORBIDDEN |
| T03 | Destino no miembro | TARGET_NOT_MEMBER |
| T04 | Destino bloqueado M02 | TARGET_BLOCKED |
| T05 | Escritura directa UPDATE membresía | Denegada por RLS (sin INSERT/UPDATE/DELETE authenticated) |

---

## 5. Sucursales

| ID | Caso | Expectativa |
|----|------|-------------|
| B01 | Crear sucursal con `manage_branches` | OK; `contact_phone_public=false` por defecto |
| B02 | VIEWER crea sucursal | FORBIDDEN |
| B03 | Listado privado | Teléfono visible para gestores |
| B04 | Listado público (`include_private=false`) | Teléfono null si no público |
| B05 | Sin columnas de coordenadas | Schema sin lat/lng |
| B06 | Cerrar sucursal | status CLOSED |

---

## 6. Cierre de organización

| ID | Caso | Expectativa |
|----|------|-------------|
| C01 | `close_organization` por OWNER | status CLOSED; invitaciones PENDING → REVOKED |
| C02 | Operaciones post-cierre | `ORGANIZATION_BLOCKED` / solo view |
| C03 | MEMBER intenta cerrar | FORBIDDEN |

---

## 7. Seguridad transversal

| ID | Caso | Expectativa |
|----|------|-------------|
| S01 | Actor siempre `auth.uid()` | RPC ignora actor libre |
| S02 | AccountType no concede membresía | Sin filas solo por AccountType |
| S03 | Platform role M02 no implica org | SUPERADMIN sin membresía → FORBIDDEN |
| S04 | Usuario SUSPENDED | No invita / no administra |
| S05 | Auditoría | Acciones privilegiadas en `organization_audit_log` |
| S06 | RLS no recursiva | Políticas usan helpers SECURITY DEFINER |
| S07 | Grants directos | INSERT/UPDATE/DELETE revoked en invitaciones/branches |

---

## 8. Resultado remoto

```text
PENDIENTE DE VALIDACIÓN REMOTA
```

No se ejecutó staging en esta sesión. Release bloqueado hasta checklist PASS documentado.

### Nota Etapa 5 (correctiva 021)

Se detectó y corrigió en repo (sin editar 020) que la política SELECT de `organization_branches` podía exponer `contact_phone` privado a cualquier autenticado. La migración `021_organizations_branches_rls_privacy_fix.sql` restringe SELECT a `organization.view_private`; el listado público permanece vía RPC allowlist. **B04** debe revalidarse en staging tras aplicar 021.
