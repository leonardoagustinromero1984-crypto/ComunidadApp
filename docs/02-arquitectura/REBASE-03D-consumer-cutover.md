# REBASE-03D — Consumer cutover to canonical Staging

**Producto:** LeoVer  
**Fecha:** 15 de agosto de 2026  
**Rama:** `main`  
**HEAD al inicio:** `679fd3d63197b7318e17c0b48351e0719563c212`  
**Tipo:** CONSUMER RECONCILIATION — App / Web / KMP. Sin commit. Sin producción.

**Gobierno:** Master v1.2 > D01 v1.3 > REBASE-03B > REBASE-03C. REBASE-03A es evidencia histórica.

**Veredicto:** `REBASE_03D_BLOCKED` — contratos de consumidor avanzaron; el schema 03C no cubre writes obligatorios (foto de mascota, update de pet, adopción pública).

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
| `UNRELATED_WIP_PRESERVED` | YES (176 paths al inicio) |

Dev/QA apunta a `https://tobqbddfcyitwgbkthhy.supabase.co` vía `local.properties` (gitignored) y `web/.env.local` (gitignored). Clave de cliente: publishable. Nunca service_role.

---

## 2. Inventory (active consumers)

Clasificación al inicio (runtime/app/shared/web/tests; no docs/migraciones históricas):

| Concepto | Clasificación dominante |
| --- | --- |
| `AccountType` / `account_type` | ACTIVE_RUNTIME + ACTIVE_TEST (WIP previo ya lo deprecó; SessionIdentity fuerza PERSON) |
| `AppMode` | ACTIVE_RUNTIME residual en `RolePermissions` (WIP ya prefiere `OperationalContext`) |
| Passport / Pasaporte | ACTIVE_DOMAIN / ACTIVE_UI / ACTIVE_TEST en M14; HISTORICAL_DOC fuera |
| `pets.owner_id` | ACTIVE_API residual como proyección UI, no autoridad |
| `photoUrl` / signed URL | ACTIVE_UI residual; SoT canónico = `avatar_asset_id` / `avatarFileAssetId` |
| `wystsapjfpdtoprlmizz` | no en source; estaba en config gitignored (ahora 0) |

Objetivo 03D no era borrar palabras de migraciones/docs legacy.

---

## 3. Staging config

| Consumer | Mechanism | Backend |
| --- | --- | --- |
| Android `local` / `staging` | `local.properties` → BuildConfig | canonical Staging |
| Web local | `web/.env.local` `NEXT_PUBLIC_SUPABASE_*` | canonical Staging |
| KMP | host-injected `SharedSupabaseConfig` (Android BuildConfig / iOS Info.plist) | same Staging URL |

`local.properties.example` y `web/.env.example` documentan el ref canónico. No se commitearon secretos.

---

## 4. Canonical mappings

| Legacy consumer | Canonical |
| --- | --- |
| `users` + `account_type` | `persons` (`user_id`, `username`, `display_name`, `birth_date`) |
| Signup metadata | `username` + `display_name` + `birth_date` (trigger `handle_new_user`) |
| `AccountType` / `AppMode` | identidad = PERSON; autoridad = membership / pet relationship / age / platform role |
| `m08_create_pet_with_principal` | `canon_create_pet` |
| `m08_list_accessible_pets` | `SELECT pets` (RLS holder) |
| `age_years` persistido | `PetBirth` (`EXACT_DATE` / `MONTH_PRECISION` / `YEAR_PRECISION` / `ESTIMATED` / `UNKNOWN`) |
| `m08_set_pet_avatar_asset` | **BLOCKED** — falta write de `pets.avatar_asset_id` |
| `m14_*` Passport RPCs | `VitaCoraRepository` → `canon_create_moment` / `canon_grant_vitacora` / `canon_revoke_vitacora` / `canon_create_proposal` / `canon_decide_proposal` |
| Grant scopes BASIC/CUSTOM/PASSPORT_COMPLETE | `ESSENTIAL` / `HEALTH` / `ESSENTIAL_AND_HEALTH` / `FULL_SHAREABLE` |
| `get_public_pet` | `canon_public_pet(p_code)` |
| `get_public_lost_case` / `get_public_found_case` | `canon_public_lost_found(p_code)` + filtro `kind` |
| `get_public_adoption` | **BLOCKED** — no existe `canon_public_adoption` |
| ActiveContext | PERSONAL \| ORGANIZATION (WIP `OperationalContext` extra kinds preservado) |

Módulos que aún llaman RPCs legacy (`m14_*`, feed, marketplace, …) quedan en **mock** cuando la URL es Staging canónico (`DataProvider.useLegacyRemoteModules = false`). No hay fallback runtime al proyecto legacy.

---

## 5. PREEXISTING_WIP_OVERLAP

Edición quirúrgica sobre archivos ya sucios al inicio (no overwrite):

- `User.kt`, `AuthRepository.kt`, `SupabaseAuthRepository.kt`
- `DataProvider.kt`, `NavRoutes.kt`
- `RegisterScreen.kt`, `LoginViewModel.kt`
- `SupabasePetM08RemoteDataSource.kt`
- `UserSupabaseDataSource.kt` (si ya estaba dirty)
- `web/lib/public/api.ts`, `web/lib/public/types.ts`, `web/app/(public)/mascota/[publicCode]/page.tsx`

WIP de UI V2, `OperationalContext`, `PetPhotoResolver`, catálogo de ubicación: preservado.

---

## 6. CANONICAL_SCHEMA_BLOCKER

No se parcheó SQL. Flujos detenidos:

1. **Pet photo persist / replace / reload** — `canon_register_media` existe; no hay `canon_set_pet_avatar` ni UPDATE de `pets.avatar_asset_id`; `media_assets` sin SELECT; storage sin policies de objects.  
   Entity: 1006 / 1007 / 1020.

2. **Pet profile / health write** — `canon_create_pet` no acepta sex/size/description; no hay `canon_update_pet` ni INSERT de health.  
   Entity: 1007 / 1009 / 1020.

3. **Public adoption** — falta `canon_public_adoption`. Ruta `/adopciones/[publicCode]` se preserva y responde 404 seguro.  
   Entity: 1013 / 1020.

4. **Username availability RPC** — uniqueness al insert; no hay lookup booleano. Signup UI valida formato y deja el conflicto al trigger.  
   Entity: 1002 / 1020.

5. **List holders** — `pet_responsibility_links` sin SELECT para authenticated. Multi-owner write vía `canon_add_pet_person` sí existe.  
   Entity: 1007 / 1020.

6. **Legacy profile RPCs** — `complete_profile_onboarding` / `update_my_profile` / `is_username_available` no existen. `persons_self_update` sí.  
   Entity: 1002.

---

## 7. Residuals (active)

- `AccountType` enum y campo `User.accountType` siguen compilando (WIP + tests). No son autoridad. `ACTIVE_RUNTIME_ACCOUNT_TYPE` ≠ 0.
- `AppMode` residual en `RolePermissions`.
- Rutas/archivos M14 aún dicen `passport` (navegación WIP). Dominio de primera clase: `VitaCoraRepository`.
- `Pet.ownerId` / `photoUrl` / `ageYears` quedan como proyección de display, no SoT.
- iOS Info.plist no se tocó (Windows; host debe inyectar Staging).
- Smoke Staging de signup/pet/photo/org/messaging: no ejecutado (blockers de write + no secretos en cliente para service operations).

---

## 8. Validation

| Check | Result |
| --- | --- |
| `:app:compileLocalDebugKotlin` | PASS |
| Focused unit tests (PersonAge, PetBirth, VitaCora, MockAuth, AuthViewModels) | PASS |
| Web `tsc --noEmit` | PASS |
| Web `vitest` `public-shareable` | PASS (URL Staging in env) |
| Web `next build` | PASS (APP BUILD; no OpenNext/Workers, no deploy) |
| KMP `shared:compileAndroidMain` | PASS (via Android compile) |
| KMP iOS / Xcode | SKIPPED (Windows) |
| Staging smoke writes | NOT RUN (schema blockers) |
| APK / emulator / ADB | NO |

`GIT_DIFF_CHECK` on canonical migrations: clean (no 03D edits).

---

## 9. Next (03E)

Schema-only, si Leonardo autoriza:

- `canon_update_pet`
- `canon_set_pet_avatar`
- media SELECT + storage policies
- health write RPCs
- `canon_public_adoption`
- `canon_is_username_available`
- `canon_list_pet_holders`

Sin eso, QA físico de foto de mascota y adopción pública no puede pasar.

Schema blockers resolved by REBASE-03E. See `docs/02-arquitectura/REBASE-03E-canonical-schema-gap-closure.md`.

---

## POST-03E / PRE-PHYSICAL-QA STATUS

Historical verdict remains `REBASE_03D_BLOCKED`. Schema gaps were closed by REBASE-03E (1023). REBASE-03F wires consumers only:

| 03D blocker | 03F consumer status |
| --- | --- |
| Pet photo persist / replace / reload | Wired: `canon_register_media` → storage upload → `canon_set_pet_avatar` → SELECT `media_assets` → runtime URL. No signed URL persist. `photoUrl` is not authority. |
| Pet profile / health write | Wired: `canon_update_pet` + 7 `canon_record_*` / `canon_set_pet_care_instructions`. Provenance DECLARED. |
| Public adoption | Already wired: `canon_public_adoption` + safe 404. |
| Username availability | Already wired: `canon_is_username_available`. |
| List holders | Wired: `canon_list_pet_holders` + multi-OWNER / AUTHORIZED / org responsible mapping. |

Physical QA of the Staging APK is REBASE-03F. See `docs/02-arquitectura/REBASE-03F-consumer-closure-physical-qa.md`.
