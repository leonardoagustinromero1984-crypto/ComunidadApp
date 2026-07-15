# M02 — Cierre Etapa 2: Contratos de perfil y autorización

**Fecha:** 2026-07-15  
**Rama:** `m02/etapa-2-contratos-perfil-autorizacion`  
**Módulo:** M02 — Usuarios, Roles y Permisos  
**Estado de entrada:** Auditoría aprobada  

---

## 1. Rama y commits

| Ref | SHA / nota |
|-----|------------|
| M01 consolidado | `2bea0ed` — `feat: complete M01 stage 4 recovery, account security and deletion` |
| Specs M02 en rama | `a758b0c` — audit + Etapa 2 spec |
| Base trabajo Etapa 2 | `a758b0c` |
| WIP GPS/mapas/pagos | **No** incorporado |

---

## 2. Decisiones aplicadas (D-M02-01…10)

| ID | Aplicación en Etapa 2 |
|----|------------------------|
| D-M02-01 | `displayName` en dominio; mapeo desde `name` |
| D-M02-02 | `Username` + validadores; no SQL aún |
| D-M02-03 | AccountType **quitado** de edición; save conserva legacy |
| D-M02-04 | Contrato `UserPrivacySettings` (tabla en Etapa 3) |
| D-M02-05 | Roles/permissions tipados en dominio (tablas en Etapa 4) |
| D-M02-06/07 | Sin migraciones / RLS / Storage ownership (Etapa 3) |
| D-M02-08 | Moderación oculta y ruta rechazada sin `moderation.view` |
| D-M02-09 | Sin asumir 014 remoto |
| D-M02-10 | Permisos vía `PermissionRepository` (mock/stub), no JWT |

---

## 3. Archivos creados

| Archivo |
|---------|
| `domain/user/UserProfile.kt` |
| `domain/user/UsernameValidators.kt` |
| `domain/user/UserProfileMapper.kt` |
| `domain/authorization/Authorization.kt` |
| `data/repository/PermissionRepository.kt` (+ Mock + StubSupabase) |
| `test/.../UsernameValidatorsTest.kt` |
| `test/.../AuthorizationServiceTest.kt` |
| `docs/02-arquitectura/M02-etapa-2-cierre.md` (este) |

## 4. Archivos modificados

| Archivo | Cambio |
|---------|--------|
| `DataProvider` | `permissionRepository` |
| `ModulePermissions` / `RolePermissions` | `canModerateContent` → **siempre false** |
| `ProfileViewModel` / `ProfileScreen` | `canViewModeration` vía authz |
| `AdminModerationViewModel` / Screen | Gate `moderation.view`; popBack si denegado |
| `EditProfileScreen` / ViewModel | Sin selector AccountType; no muta `accountType` |

## 5. Separación Auth / perfil

| Tipo | Contenido |
|------|-----------|
| `AuthUser` (M01) | id, email, emailVerified, sesión |
| `UserProfile` (M02) | displayName, username, privacy, setup/account status, … |
| `PublicUserProfile` | Allowlist: sin email/phone/modules |

`User` data model permanece como bridge legacy hasta Etapa 3.

## 6. Autorización

- Deny-by-default (`AuthorizationService`).
- Roles: USER, MODERATOR, ADMIN, SUPERADMIN.
- Mock: default USER; seed de roles solo vía `setRolesForTests`.
- Stub remoto Etapa 2: solo USER (sin tablas).
- AccountType / `active_modules` / `LeoverModule.ADMIN` **no** conceden permisos.

## 7. Pruebas

| Control | Resultado |
|---------|-----------|
| `testDebugUnitTest` | Ver recuento post-corrida (§7.1) |
| `assembleDebug` | SUCCESS |
| `lintDebug` | 0 errors |
| Migraciones nuevas | **Ninguna** |
| M03 | **No iniciado** |

### §7.1 Recuento

| Métrica | Valor |
|---------|------:|
| Tests | **96** |
| Failures | **0** |
| lint errors | **0** |

---

## 8. Fuera de alcance (correcto)

- Migraciones SQL / username en DB  
- Onboarding UI completo / `ProfileSetupRequired` gate  
- RLS / Storage ownership  
- Tablas reales de roles / RPC `has_permission`  
- M03  

## 9. Riesgos / siguiente

- Stub remoto no eleva a MODERATOR hasta Etapa 4.  
- Etapa 3: columnas perfil + privacidad + RLS proyección + onboarding.  
- Soft-fail: `User` legacy sigue portando email en sesión; proyección pública tipada ya existe.

**Etapa 2 completa.** No iniciar Etapa 3 hasta aprobación.
