# REBASE-03F — Consumer closure / physical QA prep

**Producto:** LeoVer  
**Fecha:** 16 de agosto de 2026  
**Rama:** `main`  
**HEAD canónico:** `679fd3d63197b7318e17c0b48351e0719563c212`  
**Tipo:** CONSUMER WIRING — App / Web / KMP. Sin schema. Sin commit. Sin producción.

**Gobierno:** Master v1.2 > D01 v1.3 > REBASE-03B > REBASE-03C. 03D es evidencia. 03E cerró schema. 03F cablea consumidores.

**Veredicto esperado al generar APK:** `REBASE_03F_READY_FOR_MANUAL_QA`  
**QA físico:** `PHYSICAL_QA_RESULT = PENDING_USER`  
**Commit:** `READY_FOR_CONSUMER_COMMIT = NO`

---

## 1. Isolation

| Key | Value |
| --- | --- |
| `CANONICAL_STAGING_PROJECT` | `tobqbddfcyitwgbkthhy` |
| `LEGACY_PROJECT` | `wystsapjfpdtoprlmizz` |
| `LEGACY_BACKEND_CHANGED` | NO |
| `CANONICAL_SCHEMA_CHANGED` | NO |
| `CANONICAL_MIGRATIONS_MODIFIED` | NO |
| `NEW_CANONICAL_MIGRATION_CREATED` | NO |
| `PRODUCTION_CHANGED` | NO |
| `COMMIT_PERFORMED` | NO |
| `EMULATOR_STARTED` | NO |
| `ADB_USED` | NO |

---

## 2. Consumer wiring completed

| Vertical | Contract | Consumer |
| --- | --- | --- |
| PET PHOTO | `canon_register_media` → storage INSERT → `canon_set_pet_avatar` → reload `pets.avatar_asset_id` → SELECT `media_assets` → runtime URL | `CanonicalFileUploadRepository`, `CanonicalFileObjectUploader`, `CanonicalFileAssetRepository`, `CanonicalFileDownloadRepository` |
| PET EDIT | `canon_update_pet` | `SupabasePetM08RemoteDataSource.updatePetProfile` |
| HEALTH | 7 RPCs `canon_record_*` / `canon_set_pet_care_instructions` | `updatePetHealth` + form alergia/medicación/condición |
| FAMILY | `canon_list_pet_holders` + `canon_add_pet_person` / `canon_set_org_responsible` / `canon_open_custody` | `DataProvider.petResponsibilityRepository` on Staging |
| VITACORA | Live pet + Health. No Passport snapshot | `CanonicalVitaCoraProjectionRepository` + `CanonicalVitaCoraRepository` |
| USERNAME | `canon_is_username_available` | `UserSupabaseDataSource` |
| PUBLIC ADOPTION | `canon_public_adoption` + safe 404 | `web/lib/public/api.ts` |

Photo rules:

- No persistir signed URL  
- `photoUrl` no es authority  
- Replace no llama storage DELETE (cleanup DEFERRED)  
- Diagnóstico por paso: `REGISTER_MEDIA` / `STORAGE_UPLOAD` / `SET_PET_AVATAR` / `MEDIA_SELECT` / `SIGNED_URL_RESOLUTION`

Holders: primer PERSON OWNER → PRINCIPAL; demás OWNER + AUTHORIZED → CO_RESPONSIBLE; ORGANIZATION RESPONSIBLE visible. No `pets.owner_id` como autoridad.

---

## 3. Legacy active residuals

Clasificación en `app/src/main`, `shared/`, `web/` (no docs/migrations históricas):

| Concepto | Clasificación | Nota |
| --- | --- | --- |
| `AccountType` | ACTIVE_RUNTIME residual | `RolePermissions` / `ModulePermissions` / `AccountTypeDropdown` / `toLegacyOperationalContext`. Signup no muestra picker. No es autoridad de pet/foto. |
| `AppMode` | ACTIVE_RUNTIME residual | Overloads en `ModulePermissions`. ActiveContext decide nav, no pet photo. |
| Passport / Pasaporte | COMPATIBILITY_ONLY + HISTORICAL | QA path visible = VitaCora. Identificadores M14 / error mapper residuales. |
| `pets.owner_id` / `Pet.ownerId` | COMPATIBILITY_ONLY | Proyección display. Holders = `canon_list_pet_holders`. |
| `photoUrl` | COMPATIBILITY_ONLY | Fallback legacy HTTPS si no hay `avatarFileAssetId`. |
| `wystsapjfpdtoprlmizz` | COMPATIBILITY_ONLY | Constante de detección en `CanonicalBackend`. No es URL de runtime. |

`ACTIVE_RUNTIME_ACCOUNT_TYPE` ≠ 0  
`ACTIVE_RUNTIME_APPMODE` ≠ 0  
`ACTIVE_RUNTIME_PASSPORT` = 0 en producto visible de QA

---

## 4. CONTRACT_ONLY modules (fuera de QA 03F)

Siguen mock cuando `useLegacyRemoteModules = false` (Staging canónico):

feed, lost/found remote, adoption write (app), marketplace, messaging, bookings, daycare, provider, M14 credentials/verification/operations, M28 veterinary portal, moderation/admin extras, organization verification.

QA 03F no los cubre.

---

## 5. Known limitations (no schema request)

1. **Health SELECT** — 1020 revoca GRANT de tablas de salud. Writes van por RPC. Hydrate intenta SELECT y, si RLS deniega, el pet carga sin listas. QA14 puede fallar al recargar aunque el write haya sido real. No se pide migration nueva en 03F.  
2. **Holder revoke** — no hay RPC 1023. UI falla limpio: `PET_HOLDER_REVOKE_DEFERRED`.  
3. **Old avatar cleanup** — DEFERRED. Storage DELETE sigue protegido.  
4. **`canon_register_media` visibility** — default PRIVATE. `public-media` se resuelve como URL pública de bucket en runtime.

---

## 6. Validation

| Check | Result |
| --- | --- |
| Focused unit tests (media, holders, health RPCs, VitaCora, wiring) | PASS |
| `:app:compileLocalDebugKotlin` | PASS |
| Web `vitest` `public-shareable` | PASS |
| Web typecheck / next build | SKIPPED (sin cambio de contrato web) |
| KMP Android compile | PASS (vía app compile) |
| KMP iOS / Xcode | SKIPPED (Windows) |
| `assembleLocalDebug` | PASS |
| APK | `apk/LeoVer-REBASE-03F-STAGING.apk` (38571676 bytes, SHA256 `8DFF2EEB8ED0BB419D7B6C64A9AF272624807D04BEE97DAFFF7E06AA6BE75CD2`) |
| Emulator / ADB | NO |

---

## 7. Manual QA

Checklist: `docs/02-arquitectura/REBASE-03F-physical-qa-checklist.md`

`READY_FOR_MANUAL_PHYSICAL_QA = YES` cuando el APK Staging esté copiado.  
`PHYSICAL_QA_RESULT = PENDING_USER`  
`READY_FOR_CONSUMER_COMMIT = NO`

---

## 8. PRE-PHYSICAL-QA FINAL GATE

REBASE-03F.1 — residual authority cleanup + Health read proof. No schema change. No commit.

### AccountType residual cleanup

`ACTIVE_RUNTIME_ACCOUNT_TYPE_AUTHORITY = 0`.

Permission overloads in `RolePermissions` / `ModulePermissions` ignore `AccountType` and delegate to `OperationalContext.Personal`. `defaultModules()` / `toUserCategory()` / `ServiceCategory.fromAccountType()` no longer branch on type.

Remaining residuals (not authority):

| Residual | Classification |
| --- | --- |
| `User.accountType` + signup forced PERSON | COMPATIBILITY |
| `SessionIdentity` / JWT mapper | COMPATIBILITY |
| `bottomNavItemsFor(AccountType)` + `toLegacyOperationalContext` | NAVIGATION (tests / unused runtime bar uses `OperationalContext`) |
| `AccountTypeDropdown` | DEAD_CODE (no screen callers) |
| `AccountType.toDisplayName()` | DISPLAY |
| Comments / deny-path notes | HISTORICAL_NAME |

`ACTIVE_RUNTIME_ACCOUNT_TYPE` is still a compatibility residual (field + enum + nav shim). Not replaced by another account-type enum.

### AppMode residual cleanup

`AppMode` and `toAppMode()` removed from runtime. Navigation / publish UX uses `OperationalContext` only.

`ACTIVE_RUNTIME_APPMODE = 0`  
`ACTIVE_RUNTIME_APPMODE_AUTHORITY = 0`

### Health read investigation

Inspected canonical 1009 / 1020 / 1023 and all `canon_*` functions.

| Question | Finding |
| --- | --- |
| Health tables | Exist (`pet_allergies`, `pet_medications`, `pet_declared_vaccinations`, `pet_parasite_treatments`, `pet_conditions`, `pet_weights`, `pet_care_instructions`, `pet_declared_health`) |
| Write path | 1023 `canon_record_*` + `canon_set_pet_care_instructions` (DECLARED). Android still wired. |
| Read RPC | None (`canon_get_pet_health` / list RPCs absent) |
| Table SELECT grant | None. 1020 `REVOKE ALL` + no health SELECT policy. 1023 grants SELECT only on `media_assets` / `pets`. |
| VitaCora composition RPC | Moments/grants only. Does not return Health. |
| `canon_public_pet` | `public_code, name, species, sex, locality_id` — no Health |

`CANONICAL_HEALTH_READ_PATH = NONE`

Unauthorized client SELECT (`hydrateDeclaredHealth`) was removed. It was not a legitimate read path.

`HEALTH_WRITE_REAL_STAGING = YES`  
`HEALTH_READ_AFTER_RELOAD_REAL_STAGING = NO`  
`CANONICAL_SCHEMA_BLOCKER_HEALTH_READ = YES`

Recommended contract (not implemented): `canon_get_pet_health(p_pet_id)` or narrowly scoped read RPCs. Do not open table SELECT. Do not create 1024 without authorization.

### Mock / fallback audit (localDebug Staging)

| Vertical | Runtime mock | Legacy RPC | Legacy backend |
| --- | --- | --- | --- |
| SIGNUP | 0 | 0 | 0 |
| PET | 0 | 0 | 0 |
| PET PHOTO | 0 | 0 | 0 |
| HEALTH write | 0 | 0 | 0 |
| HEALTH read | n/a — schema blocker | 0 | 0 |
| VITACORA | 0 | 0 | 0 |
| FAMILY / HOLDERS | 0 | 0 | 0 |
| PUBLIC ADOPTION | 0 | 0 | 0 |

`PET_HOLDER_REVOKE_DEFERRED = YES` (not a current physical QA blocker).

### Final APK metadata

Rebuilt after 03F.1 runtime cleanup.

- Path: `apk/LeoVer-REBASE-03F-STAGING.apk`
- Size: `38571491`
- SHA256: `D13246484FA827D2FA49F9850743A23AB10473A8923228FD7B4B7556266B37A5`
- Environment: STAGING (`tobqbddfcyitwgbkthhy`)

`READY_FOR_FULL_MANUAL_QA = NO`  
`FINAL_VERDICT = REBASE_03F_HEALTH_SCHEMA_BLOCKED`

### RESOLVED BY REBASE-03G

Historical 03F.1 verdict above is unchanged.

Forward migration **1024** added `canon_get_pet_health` and `canon_end_pet_responsibility`. Android now reads Health after reload through that RPC. Holder revoke is no longer deferred. See `REBASE-03G-full-v1-canonical-consumer-migration.md`.
