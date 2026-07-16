# M03 — Cierre Etapa 2: Contratos de organizaciones, membresías y autorización

**Fecha:** 2026-07-15  
**Rama:** `m03/etapa-2-contratos-organizaciones`  
**Módulo:** M03 — Organizaciones y Equipos  
**Estado de entrada:** Auditoría inicial aprobada (`M03-auditoria-inicial.md`)  

---

## 1. Rama y commits

| Ref | SHA / nota |
|-----|------------|
| Auditoría M03 consolidada | `f71d579` — `docs: add M03 initial organizations and teams audit` |
| Spec Etapa 2 | `0b3c2a5` — `docs: add approved M03 stage 2 organization membership and auth contracts spec` |
| Base trabajo Etapa 2 | `0b3c2a5` |
| WIP GPS/mapas/pagos | **No** incorporado |
| Merge a `main` | **No** |

---

## 2. Decisiones aplicadas (D-M03-01…08)

| ID | Aplicación en Etapa 2 |
|----|------------------------|
| D-M03-01 | Entidad nueva `Organization`; shelters/services solo vía `OrganizationResourceLink` |
| D-M03-02 | Link SERVICE_PROFILE PERSONAL **o** ORGANIZATION (exclusivo; dual inválido) |
| D-M03-03 | Sin creación automática desde AccountType (`linkFromAccountType()` → null) |
| D-M03-04 | `FOSTER_HOME` **no** es `OrganizationType` (capacidad personal) |
| D-M03-05 | Contrato verificación + bloqueo auto-verificación; sin docs M04 |
| D-M03-06 | Contacto institucional privado por defecto; `PublicOrganization` allowlist |
| D-M03-07 | Roles internos `OrganizationRoleCode` separados de platform roles M02 |
| D-M03-08 | `OrganizationAuthorizationService` deny-by-default |

---

## 3. Archivos creados

| Archivo |
|---------|
| `domain/organization/Organization.kt` |
| `domain/organization/OrganizationSlugValidators.kt` |
| `domain/organization/OrganizationInvitation.kt` |
| `domain/organization/OrganizationResourceLink.kt` |
| `domain/organization/authorization/OrganizationAuthorization.kt` |
| `data/repository/OrganizationRepositories.kt` (interfaces + mocks) |
| `test/.../organization/OrganizationValidatorsTest.kt` |
| `test/.../organization/OrganizationInvitationRulesTest.kt` |
| `test/.../organization/OrganizationResourceLinkRulesTest.kt` |
| `test/.../organization/authorization/OrganizationAuthorizationServiceTest.kt` |
| `docs/02-arquitectura/M03-etapa-2-cierre.md` (este) |

## 4. Archivos modificados

| Archivo | Cambio |
|---------|--------|
| `DataProvider` | Repos org solo mock (sin Supabase) |
| `PublishViewModel` | `shelterId = null`; AccountType ya no asigna shelter institucional |

---

## 5. Contratos entregados

### Organización

- Tipos: SHELTER, RESCUE_GROUP, NGO, VETERINARY_CLINIC, PET_SHOP, TRAINING_CENTER, WALKER_AGENCY, OTHER  
- Estados: DRAFT, ACTIVE, RESTRICTED, SUSPENDED, CLOSED, REJECTED  
- Verificación: NOT_REQUESTED, PENDING, VERIFIED, REJECTED, EXPIRED  
- Slug: 3–50, normalización, reservados, sin `--` / espacios  
- OTHER exige descripción; proyección pública sin PII salvo opt-in  

### Membresía y autorización

- Roles internos: OWNER, ADMIN, MANAGER, MEMBER, VIEWER  
- Permisos tipados `organization.*` (sin adopciones/pagos/marketplace)  
- Deny-by-default: sin membresía ACTIVE → negar  
- Cuenta M02 SUSPENDED/BANNED → negar  
- Org SUSPENDED/CLOSED/REJECTED → no publicar / no administrar  
- Último OWNER protegido (remove + demote)  
- ADMIN no remueve/asigna OWNER ni se autoeleva  
- AccountType / active_modules / roles M02 **no** conceden membresía  

### Invitaciones

- Estados PENDING / ACCEPTED / DECLINED / REVOKED / EXPIRED  
- Expirables, revocables, un solo uso  
- No invitar OWNER por invitación  
- Token no se filtra en `toString()`; mock no otorga membresía hasta accept  

### Legacy

- `SHELTER_LISTING` / `SERVICE_PROFILE` vinculables  
- Shelter sin org válido; service personal u organizacional válido  
- Sin migraciones ni cambios RLS  

---

## 6. Fuera de alcance (respetado)

- Migraciones SQL / tablas `organizations`  
- RLS / Storage logos  
- UI completa de organizaciones  
- Supabase remoto para org  
- GPS / mapas / pagos  
- M04  

---

## 7. Pruebas y calidad

| Control | Resultado |
|---------|-----------|
| Suite dominio M03 | Validators, Authorization, Invitations, ResourceLink (unit) |
| `assembleDebug` | **SUCCESS** |
| `testDebugUnitTest` | **157** tests, **0** failures, **0** errors |
| `lintDebug` | **SUCCESS** |

---

## 8. Checklist Etapa 2

- [x] Auditoría consolidada (`f71d579`)  
- [x] Rama `m03/etapa-2-contratos-organizaciones`  
- [x] Organization separado de UserProfile  
- [x] Tipos y estados tipados  
- [x] Slug central y testeado  
- [x] Roles internos separados de M02  
- [x] Permisos internos tipados  
- [x] Deny-by-default  
- [x] Último OWNER protegido  
- [x] AccountType no concede membresía  
- [x] active_modules no concede membresía  
- [x] Invitaciones tipadas y expirables  
- [x] Foster home permanece capacidad personal  
- [x] Contratos de link legacy sin migración automática  
- [x] Sin migraciones  
- [x] Sin M04  
- [x] `assembleDebug` SUCCESS  
- [x] `testDebugUnitTest` 157 / 0 failures / 0 errors  
- [x] `lintDebug` SUCCESS  
- [x] Cierre creado  

---

## 9. Parada

**No** se inicia Etapa 3 ni M04.  
**No** merge a `main`.  

Siguiente habilitado solo tras aprobación: **M03 Etapa 3** (persistencia, RLS, perfil org).
