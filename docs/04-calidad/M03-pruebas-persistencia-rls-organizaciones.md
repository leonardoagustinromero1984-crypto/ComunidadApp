# M03 — Pruebas de persistencia, perfil y RLS de organizaciones (Etapa 3)

**Módulo:** M03 — Organizaciones  
**Alcance:** SQL/RLS documentado (`019`) + suite unitaria Android  
**Estado remoto:** Migraciones `014`–`019` **PENDIENTE DE VALIDACIÓN REMOTA** (no se afirma deploy ni checklist en staging/producción en esta etapa).

---

## 1. Suite SQL / RLS (manual / staging)

Ejecutar solo en proyecto de staging o local. **No** correr destructivos en producción.

### 1.1 Fundación tablas / catálogo (`019_organizations_foundation.sql`)

| Caso | Esperado |
|------|----------|
| Catálogo roles OWNER…VIEWER | Seed / filas sistema presentes |
| Catálogo permisos `organization.*` | Códigos allowlist; matriz rol→permiso |
| `organizations` constraints | type, status, verification, slug, country, display_name |
| `organization_memberships` | UNIQUE (org, user); role_code upper |
| Sin escritura directa `authenticated` | INSERT/UPDATE/DELETE revocados en tablas org |

### 1.2 RPCs autenticadas

| Caso | Esperado |
|------|----------|
| `create_organization` con `auth.uid()` | Crea org DRAFT + membresía OWNER ACTIVE |
| Create sin sesión / cuenta no ACTIVE | Excepción `NOT_AUTHENTICATED` / `ACCOUNT_NOT_ALLOWED` |
| Create tipo OTHER sin descripción | `OTHER_TYPE_DESCRIPTION_REQUIRED` |
| `update_my_organization` sin `organization.update` | `FORBIDDEN` |
| Update no cambia status/verification | Columnas administrativas intactas |
| `get_my_organizations` | Solo orgs con membresía ACTIVE del caller |
| `get_public_organization_by_slug` | Solo ACTIVE/RESTRICTED; JSON allowlist |
| Público sin email/phone salvo flags | `contact_*` null si no públicos |
| `search_public_organizations` query &lt; 2 | `[]`; limit ≤ 50 |
| `has_org_permission` / `get_my_org_permissions` | Deny-by-default sin membresía / error |
| `request_organization_verification` | Solo a PENDING; nunca VERIFIED desde cliente |
| `link_organization_resource` / unlink | Requiere `organization.update`; tipo allowlist |

### 1.3 RLS SELECT

| Caso | Esperado |
|------|----------|
| SELECT `organizations` ajena | 0 filas (solo `is_org_member`) |
| SELECT membresías propias | OK |
| SELECT audit / status_history | Solo con `organization.view_private` |
| Catálogo roles/permisos | SELECT autenticado; sin escritura |

### 1.4 Storage (`organization-media`)

| Caso | Esperado |
|------|----------|
| Bucket privado | Path `organizations/{uuid}/logo|cover/{file}` |
| Upload con `organization.update` | OK para miembro con permiso |
| Path ajeno / `..` | Denegado |
| Signed URL | Temporal; DB guarda path, no URL eterna |

---

## 2. Suite unitaria Android (repo)

| Área | Cobertura |
|------|-----------|
| Paths media | `OrganizationMediaPathTest` |
| Mock repo Etapa 3 | `OrganizationRepositoryMockEtapa3Test` (create, OWNER, público sin PII, verification→PENDING) |
| Mapper / deny pattern | `OrganizationRowMapperDenyPatternTest` |
| Autorización dominio | `OrganizationAuthorizationServiceTest` (Etapa 2) |
| Validadores / slug | `OrganizationValidatorsTest`, `OrganizationSlugValidatorsTest` |
| Invitaciones / links | `OrganizationInvitationRulesTest`, `OrganizationResourceLinkRulesTest` |

---

## 3. Manual / smoke (mock local)

1. Perfil → **Mis organizaciones** (visible sin gate de AccountType).
2. Crear org con validación client-side → aparece en lista.
3. Editar: sin permiso → denegado; con OWNER → guarda descripción/contactos/visibilidad.
4. Solicitar verificación solo si estado NOT_REQUESTED/REJECTED/EXPIRED → queda PENDING.
5. Perfil público por slug: sin email/teléfono si no opt-in.
6. AccountType / modules no desbloquean edición ni pantallas privilegiadas.

---

## 4. Remoto

**PENDIENTE DE VALIDACIÓN REMOTA** para migraciones `014`–`019`.  
**No se afirma** aplicación ni validación en el proyecto Supabase remoto sin evidencia de deploy + checklist ejecutada.
