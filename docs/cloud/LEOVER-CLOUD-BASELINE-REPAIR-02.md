# LeoVer Cloud baseline repair 02

Child of `cursor/leover-cloud-validation-01-ea40`. Cloud JVM only. No emulator, no Maestro, no PROD, no merge to `main`.

## Cursor Environment Install Script

```bash
bash scripts/cloud/bootstrap-cloud.sh
```

Idempotent. Detects JDK 21, sets `ANDROID_HOME`, installs `platforms;android-36`, `platforms;android-36.1`, `build-tools;36.1.0`, and `platform-tools` only when missing. Does not install an emulator or system images.

Linux regression:

```bash
bash scripts/qa/run-regression.sh
```

Uses flavor `localDebug` and `--no-configuration-cache --no-daemon`. Configuration cache stays enabled in `gradle.properties`. Repair of the Gradle 9.4.1 serialization failure is separate debt.

## Environment seams

JVM `android.jar` throws `RuntimeException` with message `Stub!` or `Method … not mocked`. `AndroidAppLogger` and `ElapsedRealtime` catch only that case and keep every other `RuntimeException`. The clock fallback is a process-local `nanoTime` delta. Image tests inject an `ImageIngest` that returns `normalized = true` for image MIME types. `unitTests.isReturnDefaultValues` stays off.

## Stale expectations updated

| Test | OLD EXPECTATION | CURRENT EXPECTATION | CANONICAL EVIDENCE |
| --- | --- | --- | --- |
| Profile actor count | 5 actors, label Persona | 6 actors, Personal + Hogar de tránsito | `ProfileActorTaxonomy.FIRST_LEVEL_ACTOR_COUNT` |
| Foster first level | `FOSTER_IS_FIRST_LEVEL_ACTOR` false | true | `PhysicalQaFix02Contracts`, `ContextRegistry` |
| Home brand | LeoVer · Persona | LeoVer · Personal | `ContextHumanLabels.shortLabel` |
| Selector subtitle | perfil Persona es la base | perfil Personal es la base | `Onb02Copy.SELECTOR_SUBTITLE` |
| Foster setup | `home` | `foster_placements` | `FunctionSetupMapping.routeFor(FOSTER)`, `NewContextActivation.landingRoute` |
| T00 size | 4 steps, community at index 2 | 5 steps, privacy at index 2, community at index 3 | `TutorialCatalog.t00` |
| VitaCora reel title | Se guardó un Reel | Se guardó un Clip | `VitaCoraHistoryPresentation.titleFor` |
| QR `buildPayload` | `leover://passport/` | `https://leover.com.ar/mascota/` | `M14PublicQrPayloadService.buildPublicHttpsUrl` |
| QR short code | `BAD` rejected | HTTPS payload, email still rejected | `normalizeForPayload` else branch |
| Accept application | competing apps `REJECTED` | `PAUSED` | `AdoptionApplicationRepository` / migration 1093 pause |
| Vet tab | `my_business` | `professional_hub` | `NavRoutes.PROFESSIONAL_HUB` |
| Verification source | `actor_verifications`, filters hidden | `leover_verification_requests`, filters visible | `VerificationDisplayPolicy` |
| Passport create | explicit call shows "VitaCora lista" | init auto-opens; message stays null | `M14PetPassportViewModel` `autoOpenAttempted` |
| Photo reset | `reset()` returns `FILL` | `reset()` keeps `fitMode`; `withMode` changes mode | `PhotoCanvasTransform.reset` |
| Existing email signup | resend + "iniciá sesión" | title "Este correo ya está registrado", no resend flag | `RegisterViewModel.register` failure copy |

## Misconfigured scans repaired

Source contracts now point at the current implementation: crop launcher instead of `AvatarPhotoEditorScreen`, `encodeAlreadyCropped`, `AppNavRestoreStore.read/write`, `currentUserOrNull`, `qa_restore_insertable` for admin identities, Sumate title `Adopción`, no followers row, provider card `Ver perfil`, composer `selectedPetId`, `repo.getSecondaryItem`, responsibilities `maxLines = 1`, claim copy "El caso fue asignado…", diagnostic `errorCode=LF-CREATE-DB` plus `sqlstate=42883`, help replay `onFinished = { setupRoute ->`.

`UiRegressionGateTest` skips `import` lines and `supabase.postgrest` client calls. User-facing banned tokens still fail the gate.

## Possible product bugs (behavior not changed)

| Case | Result |
| --- | --- |
| finalizeAdoption owner / ARCHIVED | TEST WAS WRONG for owner transfer. Canonical `m09_finalize_adoption` raises `ADOPTION_USE_CANONICAL_TRANSFER` (migration 1093). In-memory mock still returns success, leaves the pet ACTIVE, and writes `ADOPTION_EVALUATION_DONE`. CONFIRMED BUG in the mock only: it does not refuse direct finalize. |
| Edit existing pet | TEST WAS WRONG. Non-UUID `pet-1` never enters edit mode. UUID fixture enters edit mode. |
| Deceased lock | TEST WAS WRONG for the same id. UUID + `DECEASED` sets the lock and blocks save. |
| Legacy profile/health/fetch | TEST WAS WRONG. Create always follows with profile RPC; empty health skips health RPC; `fetchPetById` requires a UUID. |

## P0 contracts

| Gap | Status |
| --- | --- |
| Migration 1096 matching `search_path` | COVERED (`_canon_match_found_to_lost` sets `public, extensions` and calls `extensions.ST_Distance`). Not a live PostGIS call. |
| Verification PENDING refresh | PARTIAL. Source sets `PENDING` then `refresh()`. LIVE_STAGING_REQUIRED for the round trip. |
| Profile switch returns Home | COVERED (`USE_LEOVER_AS` `onSelected` navigates `HOME`). Not a device run. |
| Community dirty state | COVERED. Filters survive `MAP`. Business name, description, and contact survive `updateMapPin`. |
| Direct QR route | COVERED. `m14/pets/{petId}/share` opens `M14PassportShareScreen`. |
| Professional private history | PARTIAL. `m28_list_pet_cares` filters `clinic_id` and calls `_m28_require_care_read`. Direct table policy is `using (false)`. LIVE_STAGING_REQUIRED for a second clinic. |

## Lint

NewApi: `RouteArgDecoder` uses `URLDecoder.decode(String, String)`. `DeviceLocationCompat` uses `isLocationEnabled` on API 28+ and `Settings.Secure.LOCATION_MODE` on API 26–27. minSdk stays 26.

Compose: `MyPetsScreen` and `ResponderBaseLocationScreen` collect the active context. The map pin `MarkerState` is remembered.

Visible debt, not baselined:

- Media3 `UnsafeOptInUsageError` in `SocialMediaPipeline.kt` and `StoryViewerScreen.kt`
- `MissingTranslation` because `values-es-rAR` does not repeat the Spanish default strings

## Configuration cache

DISABLED FOR CLOUD RUNNER ONLY (`--no-configuration-cache`). `org.gradle.configuration-cache=true` is unchanged.
