# LeoVer Cloud Validation 01

Audit only. No product behavior change, no test disabling, no expected-value edits, no emulator, no Maestro, no PROD, no merge.

Base snapshot: `cloud-handoff/leover-20260925` at `46a9f23d5a705f512a0a1ec7916f04d330e7f7c7`.
Max canonical migration in that snapshot: **1097** `20260925220000_1097_person_username_login.sql`.

The earlier handoff figure **2611/2653 PASS, 42 FAIL** and **43 lint errors** was **not reproduced** on this image. Numbers below are from this run.

## Commands actually run

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleLocalDebug :app:testLocalDebugUnitTest --continue --no-daemon
./gradlew :app:lintLocalDebug --no-configuration-cache --no-daemon
./gradlew :shared:testAndroidHostTest --no-configuration-cache --no-daemon
```

Variant is `localDebug`. Staging and production flavors stay disabled unless the task name contains `staging` or `production`. `local.properties` was created only with `sdk.dir` and is gitignored. No Supabase credentials. Local flavor logged `SUPABASE_ENABLED=false` (mock mode).

`connectedAndroidTest` was not run.

## Environment

The Cloud image did not contain an Android SDK or `pwsh`. JDK was already OpenJDK 21. This run installed Android cmdline-tools plus `platforms;android-36.1` and `build-tools;36.1.0` to match `compileSdk { release(36) { minorApiLevel = 1 } }` and AGP 9.2.1.

`org.gradle.configuration-cache=true` in `gradle.properties` makes Gradle 9.4.1 fail the build after the tasks finish: five `localDebug` tasks cannot be serialized (`assembleLocalDebug`, bundle jars, `packageLocalDebugResources`, `packageLocalDebug`). The APK was still produced.

## Android assemble

PASS for the artifact.

- Task `:app:compileLocalDebugKotlin` completed.
- Task `:app:assembleLocalDebug` completed.
- APK: `app/build/outputs/apk/local/debug/app-local-debug.apk` (57 324 243 bytes).
- Gradle process exit code was **1** because unit tests failed and the configuration cache store failed.

## Unit, contract, and static security tests

App JVM (`:app:testLocalDebugUnitTest`):

| | |
| --- | --- |
| TOTAL | 3023 |
| PASS | 2927 |
| FAIL | 96 |
| SKIP | 0 |
| Result | FAIL |

Shared host (`:shared:testAndroidHostTest`): **390/390 PASS** (32 classes). `allTests` was not used because it also schedules iOS targets.

Static security, RLS, ACL, rate-limit, and signed-URL tests that live under `app/src/test` ran inside the 3023. The historical and security classes checked below passed. Live STAGING SQL probes were not executed.

## Why this is not the previous 42

This repo does not set `android.testOptions.unitTests.isReturnDefaultValues`. On a plain `android.jar`, unmocked Android methods throw. This run failed on three Android stubs:

- `android.util.Log`
- `android.os.SystemClock.elapsedRealtime`
- image ingest, which catches the bitmap/decode failure and returns `MEDIA-DECODE-01`

A previous machine with Robolectric or `returnDefaultValues` would not produce the same 96. There is no log of the 42 in this workspace, so they are not reclassified one by one. The classification below covers **every failure of this run**.

## 96 failures

| Group | Count | Priority |
| --- | --- | --- |
| A. Environment / Android stubs not mocked | 41 | P0 for a trustworthy Cloud baseline |
| B. Obsolete or misconfigured test | 22 | P1 |
| C. Expectation left behind by valid product change | 26 | P1 |
| D. Possible product bug | 7 | P1, confirm before fixing |
| E. Other | 0 | — |

### A. Environment (41)

Cause: JVM unit tests call Android framework methods that `android.jar` does not implement. The failure is the test harness, not the assertion text. Do not delete the tests. Set `unitTests.isReturnDefaultValues` only after checking it does not hide real failures, or stub `Log`, `SystemClock`, and image decode in the tests that need them.

`android.util.Log` (8):

- `AuthViewModelsTest.register_existing_confirmed_email_does_not_succeed`
- `AuthViewModelsTest.register_double_submit_ignored_while_loading`
- `ProfileOnboardingViewModelTest.invalid_username_blocks_identity_step`
- `ProfileOnboardingViewModelTest.happy_path_completes_onboarding`
- `ProfileOnboardingViewModelTest.taken_username_not_available`
- `ProfileOnboardingViewModelTest.existing_signup_username_skips_identity_and_is_not_reasked`
- `ProfileOnboardingViewModelTest.reserved_username_not_available`
- `ProfileOnboardingViewModelTest.location_step_requires_province_and_locality`

`SystemClock.elapsedRealtime` (25):

- `MarkPetDeceasedViewModelTest` (6): `markDeceased_blocked_withoutCapability`, `markDeceased_success_whenActiveAndCapable`, `markDeceased_mapsAlreadyDeceased`, `governance_hidden_whenDeceased`, `restore_success_whenArchivedAndCapable`, `restore_blocked_whenDeceased`
- `PetDetailSmokeRegressionTest` (13): every method in the class failed
- `M08IntegrationRegressionTest` (6): `markDeceased_thenDetailReflectsStatus`, `createAndRetrievePet_throughFormAndDetail`, `detailWithEmptyHealth_loadsWithoutError`, `blankOrMissingPetId_exposesNotFound`, `unknownLifecycleStatus_stillLoadsDetail`, `repositoryError_onDetail_isControlled`

Image ingest mapped to `MEDIA-DECODE-01` (8). `FileUploadCoordinator.startUpload` requires `normalized == true` for images. The unit fixture never gets a decoded bitmap, so later asserts see `Failure` instead of `Success` (`ClassCastException`, `NullPointerException` on `sessionId`, `DOUBLE_SUBMIT` never reached):

- `FileUploadCoordinatorTest` (6): `mock upload reaches ready and never uses legacy bucket`, `double submit is rejected while first upload is active`, `cancel is idempotent and retry succeeds`, `safe replace keeps old until new is ready then unlinks and deletes`, `unlink and delete are separate explicit operations`, `clear removes uri locks retry and active sessions`
- `FileDisplayResolverTest` (2): `wrong organization is denied`, `signed urls expire in memory and are never stored in asset`

Recommended fix: inject a fake `ImageIngest` in those tests, and return default values or fake the clock/log for ViewModel tests. Re-run before treating any of these 41 as product bugs. `markDeceased_mapsAlreadyDeceased` showed "No tenés permiso" instead of the deceased copy; that message is downstream of the clock throw and is not classified as a product bug yet.

### B. Obsolete or misconfigured (22)

Cause: source-scan contracts (`file.contains("...")`) or a gate that matches a token outside user-visible copy. The scan no longer matches current source. Priority P1. Update the contract to the current source of truth, or narrow the scan, after a human confirms the new copy. Do not weaken asserts only to go green.

- `TutorialCopyAndRoutingTest.HELP_REPLAY_RETURNS_TO_LIBRARY_WITHOUT_SETUP` — looks for `onFinished = { navController.popBackStack() }`
- `AvatarPhotoProcessingTest.PHOTO_CONFIRM_USES_PROCESSED_FILE` — `EditProfileViewModel` no longer contains `AvatarPhotoProcessor`
- `AvatarPhotoProcessingTest.PHOTO_CANCEL_PRESERVES_CURRENT_AVATAR` — cancel path no longer assigns `pendingImageUri = null` that way
- `LeoVerVitacoraImportContractTest.copyAndNavigationUseVitaCoraAndImportEntryPoints` — admin copy no longer contains `Importaciones`
- `LeoVerPhysicalQaFix01ContractTest.PROFILE_PHOTO_DRAG_PINCH` — onboarding source no longer contains `AvatarPhotoEditorScreen`
- `LeoVerPhysicalQaFix01ContractTest.PUBLIC_CONTACT_PHONE_ONLY` — missing `Teléfono de contacto`
- `LeoVerPhysicalQaFix03ContractTest.NAV_STATE_AND_ACTIVE_CONTEXT_RESTORE` — missing `lastReadySession`
- `LeoVerPhysicalQaFix03ContractTest.PET_CREATE_PERSON_AND_RESCUER_AUTH` — missing `currentSessionOrNull`
- `LeoVerOnb03ContractTest.GOOGLE_FIRST_TAP_NO_PREMATURE_ERROR` — missing `GoogleOAuthPending.pendingUser`
- `AdminTechnicalAccessContractTest.resetPreservesTechnicalAdminIdentity` — reset SQL no longer contains that `platform_admin_identities` insert snippet
- `PersonaBottomSurfacesTest.sumate_matches_persona_reference` — missing `Adopciones`
- `PersonaBottomSurfacesTest.profile_screen_is_persona_layout_without_invented_followers` — missing `Seguidores y siguiendo`
- `PersonaBottomSurfacesTest.comunidad_screen_is_services_directory_not_social_feed` — missing `V2CompactCta`
- `Ux06PhysicalQaStabilizationTest.PHOTO_PICK_OPENS_EDITOR` — missing `AvatarPhotoEditorScreen(`
- `Ux06PhysicalQaStabilizationTest.PHOTO_CONFIRM_UPLOADS_PROCESSED_RESULT` — missing `fun confirmEditedPhoto`
- `LocalDebugDiagnosticTest.lostFoundDiagnosticIsSanitizedAndTyped` — fixture text no longer contains `errorCode=42883`
- `UiRegressionGateTest.screensDoNotExposeRawBackendErrors` — false positive. The banned token `PostgREST` matches `import io.github.jan.supabase.postgrest.postgrest` and `supabase.postgrest.rpc` in `FosterCareRequestScreens.kt`, `AdoptionGeneralProfileScreen.kt`, `ProfessionalPatientsScreen.kt`, `ProfessionalHubScreens.kt`. Those are client calls, not a raw error string shown to the user. The gate should scan user-facing strings. Calling PostgREST from a Composable is separate architecture debt.
- `LeoVerOrgSocialQaContractTest.petIdManualFieldRemovedFromStoryAndReelComposers` — missing `Agregar mascota`
- `LeoVerPreQaFinalContractTest.composersHideMusicStickersGifWithoutRemovingCode` — same missing label
- `AdminStaffEdgeAuthContractTest.historicalBreedResolvedByIdNotByCopyingPetText` — form no longer contains `repo.getBreed`
- `PetCareTransferScreenGuardsTest.transferScreen_usesCanonicalCareCopy` — responsibilities screen no longer contains `maxLines = 2`
- `LeoVerLocationAlerts01ContractTest.CLAIM_COPY_AND_STATUS` — `AlertMapScreen` no longer contains `Este caso ya fue tomado por otro colaborador.`

### C. Expectation changed by valid development (26)

Cause: product source encodes a newer rule and the test still encodes the old one. Priority P1. Retarget the test to the new rule. Do not revert the product in this audit.

Actor taxonomy is now 6. `ProfileActorTaxonomy.FIRST_LEVEL_ACTOR_COUNT = 6` and `FOSTER` ("Hogar de tránsito") is first level. Visible person label is `Personal`, not `Persona`.

- `LeoVerProfileActorTaxonomyContractTest.PROFILE_ACTOR_FIRST_LEVEL_IS_EXACTLY_FIVE`
- `LeoVerRecoveredUx01ContractTest.PROFILE_TAXONOMY_AND_TUTORIAL_UNTOUCHED`
- `LeoVerPhysicalQaFix01ContractTest.PET_FRIENDLY_IS_SECOND_LEVEL`
- `LeoVerPhysicalQaFix02ContractTest.DEFAULT_START_ACTOR_PERSON_SELECTED`
- `LeoVerPhysicalQaFix02ContractTest.ADD_FUNCTION_EXCLUDES_PERSON_AND_EXISTING_INCLUDES_FOSTER` (`FOSTER_IS_FIRST_LEVEL_ACTOR` is still asserted false)
- `LeoVerPhysicalQaFix02ContractTest.ACTIVE_CONTEXT_HOME_LABEL_IS_HUMAN` (`LeoVer · Personal`)
- `LeoVerPhysicalQaFix03ContractTest.PERSON_ALWAYS_SELECTED_LOCKED`
- `Onb02PlannerTest.t00SkipDoesNotHideMultiselectExplanation`

Foster setup now routes to `foster_placements`, not `home`.

- `Onb02TutorialRoutingTest.ORGANIZATION_PLUS_PERSONAL_FUNCTIONS_ORDER_CORRECT`
- `Onb02TutorialRoutingTest.FOSTER_TUTORIAL_DOES_NOT_CREATE_ORG`
- `Onb02TutorialRoutingTest.FOSTER_TUTORIAL_DOES_NOT_OPEN_ORGANIZATION`
- `FunctionSetupMappingTest.organizationIsLastWhenCombinedWithPersonalSetups`
- `FunctionSetupMappingTest.fosterIsPersonalAndNeverOrganization`
- `LeoVerPhysicalQaFix03ContractTest.CREATE_FOSTER_NO_RAW_ADMIN_LANDING`

Tutorial T00 is 5 steps. Step title is `Tu privacidad en LeoVer`, not `Una comunidad que está cuando hace falta`.

- `TutorialCopyAndRoutingTest.COMMON_INTRO_COVERS_REQUIRED_STORY`
- `LeoVerOnb03ContractTest.COMMON_TUTORIAL_COPY_AND_VITACORA_HEART`
- `Onb02PlannerTest.t00HasCanonicalCommonPages`

Other intentional product moves:

- `VitaCoraHistoryPresentationTest.titleFor_socialReel_isNotPlainNote` — `titleFor` returns `Se guardó un Clip` for reel/clip history.
- `M14Block3WorkflowTest.qr_payload_contains_only_public_code_scheme` and `M14Block4HardeningTest.privacy_public_projection_and_qr_without_pii` — `buildPayload` is now `buildPublicHttpsUrl` (comment: QR encodes HTTPS so a scanner without the app opens the public page). Tests still expect `leover://passport/`.
- `M09AdoptionApplicationTest.acceptApplication_rejectsOthers_andPausesPublication` — accepting one application sets the others to `PAUSED`, and `reactivateApplication` exists. The test still expects `REJECTED`.
- `BottomNavItemsForTest.business_nav_keeps_five_tabs_with_publish_center` — route is `professional_hub`, not `my_business`.
- `LeoVerPhysicalQaFix01ContractTest.VERIFICATION_NOT_SELF_DECLARED` — `VerificationDisplayPolicy.SOURCE_OF_TRUTH` is `leover_verification_requests`.
- `M14PassportCreateFromPetTest.createFromPet_success_setsPassport` and `createFromPet_alreadyExists_recoversExisting` — `M14PetPassportViewModel` init auto-calls `createFromPet()` when no passport is observed (`autoOpenAttempted`). The success test then calls create again and gets `Ya existe un pasaporte activo`. The already-exists test sees a passport before its own call. This matches the auto-open behavior.
- `PhotoCanvasTransformResetTest.resetReturnsInitialTransform` — `reset()` clears pan/zoom and keeps `fitMode`. `withMode()` is what resets the mode. The test compares against `PhotoCanvasTransform()` (`FILL`) after a `FIT` edit.

### D. Possible product bugs (7)

Not fixed. Not proven on a device. Confirm against the canonical SQL or a focused test before changing business code.

1. `M09AdoptionCompletionTest.finalizeAdoption_successPath` — in-memory `finalizeAdoption` marks the adoption `ADOPTED` and writes history `ADOPTION_EVALUATION_DONE`, leaving the pet status unchanged. The test expects pet `ARCHIVED` and `ownerId` moved to the applicant. Possible incomplete finalize in the in-memory repository. Canonical SQL was not executed.
2. `M08IntegrationRegressionTest.editExistingPet_persistsChanges` — after load, `isEditMode` stayed false.
3. `M08IntegrationRegressionTest.editBlocked_afterDeceased` — `isEditMode` was false, but `mutationsLocked` stayed false for a `DECEASED` pet. Possible missing lock.
4. `LegacyPetRepositoryAdapterTest.s06_createPet_success_callsProfileAndHealth` — expected `profileCalls == 0` (comment: atomic `createPetWithPrincipal`), actual `1`.
5. `LegacyPetRepositoryAdapterTest.s07_createPet_profileFail_partialException_keepsPet` — same, `profileCalls` was `1`.
6. `LegacyPetRepositoryAdapterTest.s13_updatePet_callsProfileAndHealth` — expected `healthCalls == 1`, actual `0`.
7. `LegacyPetRepositoryAdapterTest.s25_fetchPetById` — `assertNotNull(pet)` failed.

### E. Other

0. The `ClassCastException` and `NullPointerException` in file upload are symptoms of group A.

## Lint

`:app:lintLocalDebug` — **FAIL**.

| | |
| --- | --- |
| Errors | 62 |
| Warnings | 158 |
| Hints | 2 |

The handoff figure of 43 errors was not reproduced. There is no `lint-baseline.xml`. Lint aborts the Gradle task. `assembleLocalDebug` does not run lint, so the APK still built.

| Class | Count | Meaning |
| --- | --- | --- |
| `UnsafeOptInUsageError` (Media3 `UnstableApi`) | 41 | Known debt. 38 in `SocialMediaPipeline.kt`, 3 in `StoryViewerScreen.kt`. Annotation missing. Not a crash by itself. Blocks the lint task. |
| `MissingTranslation` | 15 | Configuration / false positive for release copy. Default `values/strings.xml` is already Spanish. `values-es-rAR/strings.xml` only overrides 5 music/share/geo strings, so lint reports the other default names as untranslated in `es`. |
| `NewApi` | 3 | Real, and a release concern on minSdk 26. `URLDecoder.decode(String, Charset)` needs API 33 in `ComunidappNavGraph.kt` (invitation routes, lines 3437 and 3466). `LocationManager.isLocationEnabled` needs API 28 in `ForegroundLocation.kt:36`. |
| `StateFlowValueCalledInComposition` | 2 | Real Compose issue. `MyPetsScreen.kt:72`, `ResponderBaseLocationScreen.kt:36`. `.value` inside composition does not subscribe. |
| `UnrememberedMutableState` | 1 | Real Compose issue. `GoogleLeoVerMap.kt:134`. State recreated every recomposition. |

Release blocking if CI treats lint errors as fatal: all 62, because the task fails. Functional risk on API 26–32: the 3 `NewApi` calls. No mass refactor in this audit.

## Regression framework

**PARTIAL.**

What exists:

- `docs/qa/regression-catalog.yaml` — rules `REG-*` and `REG-BUG-001` … `010`, status COVERED / PARTIAL / MISSING.
- `docs/qa/change-impact-map.yaml` — path to Gradle `--tests` filters.
- `docs/qa/LEOVER-REGRESSION-MATRIX.md` and `docs/qa/LEOVER-REGRESSION-FRAMEWORK.md`.
- `scripts/qa/check-regression-coverage.ps1` — read-only git diff gate.
- `scripts/qa/run-regression.ps1` — Fast by default, Maestro only with explicit flags.

What works on Linux Cloud today:

- `./gradlew :app:testLocalDebugUnitTest` and `:shared:testAndroidHostTest` after a Linux JDK 21 and Android SDK 36.1.
- Reading the YAML catalog and matrix. No PowerShell required to read them.

What depends on PowerShell / Windows:

- Both QA scripts are `.ps1`. `pwsh` is not installed here.
- `run-regression.ps1` sets `JAVA_HOME` to Android Studio JBR on `C:\`, launches `gradlew.bat`, and calls `:app:testDebugUnitTest`.
- With flavors, the task that exists and runs is `:app:testLocalDebugUnitTest`. `testDebugUnitTest` is the wrong name for this build.
- `check-regression-coverage.ps1` shells out through `cmd /c` and hand-parses YAML. `REPOSITORY_FALLBACK` and `DOMAIN_FALLBACK` have `gradle_tests: []`, so an affected run can map a change to no filter.
- Maestro, `start-leover-emulator.ps1`, and AVD `LeoVer-QA` are Windows-host only. Not started.

What is missing before future work has a mandatory regression gate on Cloud:

1. A bash runner that calls `:app:testLocalDebugUnitTest` (and `:shared:testAndroidHostTest` when `shared/` changes), with `--no-configuration-cache` until the cache problems are fixed.
2. The same runner must apply `change-impact-map.yaml` without PowerShell.
3. Fix or document the configuration-cache failure so a green test run is a green Gradle exit.
4. Make Android unit tests deterministic on Linux (`Log`, `SystemClock`, image ingest) so 41 environment failures are not the baseline.
5. Close P0 holes below with cheap JVM/SQL contract tests. Maestro stays off unless a later block asks for it.
6. Do not treat `lintLocalDebug` as green until `NewApi` is fixed or explicitly baselined. A baseline that hides `NewApi` would be the wrong fix.

## Historical bug coverage

Status is for the cheap layer that Cloud can run. Maestro flows exist for some items and were **not** executed.

| Bug | Status | Evidence |
| --- | --- | --- |
| PERSON username login must not fall into admin | COVERED | `PersonUsernameLoginContractTest` 3/3 PASS. Person path uses `loginWithUsername` / `canon_begin_username_login`. Staff stays on `loginAdministrative` / `admin_begin_login`. Not a live STAGING login. |
| FOUND PostGIS geo / `search_path` | COVERED | `LeoVerCommunityCare02PhysicalQa02ContractTest.GEO_HELPER_QUALIFIES_POSTGIS` PASS. Migration 1095 contains `extensions.ST_MakePoint` and `set search_path = public, extensions`. Not a live PostGIS call. |
| Matching PostGIS `search_path` | PARTIAL | Migration `20260924200000_1096_match_notify_verify.sql` sets `search_path = public, extensions` on `_canon_match_found_to_lost` and uses `extensions.ST_Distance`. No JVM test asserts that file. Catalog points at `infra/supabase-canonical/qa/_r3_after_1096.sql`, which is a manual STAGING probe and was not run. |
| Provisional FOUND hidden from LOST selector | COVERED | `CommunityCare02PhysicalRound3Test.lostSelectorHidesFoundCasePlaceholder` PASS. `LostPetSelector` drops `originKind == FOUND_CASE`. The catalog cites `LeoVerLocationAlerts01ContractTest`, which checks provisional creation, not the selector. The selector test is the one that covers the bug. |
| Verification PENDING refresh | PARTIAL | `OrganizationVerificationRulesTest` PASS. Display labels and RPC wiring PASS in `LeoVerCommunityCare02PhysicalQa01ContractTest`. Maestro `05_verification.yaml` checks a seeded PENDING profile and explicitly does not submit, so it does not prove refresh after request. No ViewModel remount test. |
| Profile switch returns to Home | PARTIAL | Only `.maestro/flows/05b_profile_switch.yaml` (expects `Inicio`, selector not visible). No JVM test. Maestro not run. |
| QR opens directly | PARTIAL | Maestro `05c_qr_mora.yaml` taps `Compartir QR` and asserts VitaCora is not visible. No JVM navigation test for `m14/pets/{petId}/share`. Payload unit tests still expect `leover://` and failed because QR is now HTTPS (group C). |
| Required fields show `*` | COVERED | `LeoVerCommunityCare02PhysicalQa01ContractTest.REQUIRED_FIELDS_SHOW_ASTERISK_OPTIONAL_DO_NOT` PASS (`Especie *`, `Foto *`, optional `Contacto` without star). |
| Duplicate labels | PARTIAL | `LeoVerCommunityCare02PhysicalQa02ContractTest.SHARED_CHIPS_HAVE_SINGLE_LABEL` PASS for the chip row and Especie. Not every form. |
| Community form keeps data when opening the map | MISSING | `REG-BUG-010` has `tests: []`. No saveable/ViewModel test found. |

## Security regression

**PARTIAL.** Static JVM checks passed in this run. Live RLS against STAGING was not run. PROD was not contacted. Staging ref documented for later QA is `tobqbddfcyitwgbkthhy` only.

| Area | Status | What exists |
| --- | --- | --- |
| RLS | PARTIAL | Many migration static guards assert `enable row level security` (M09–M14, M28, observability). They passed as part of the 2927. Live probes under `infra/supabase-canonical/qa/` (`_sec_probe_1052.sql`, `sec02_aal2_enforcement.sql`) are not a Gradle task. |
| Pet / VitaCora ACL | PARTIAL | `FileAuthorizationTest`, `PetEditAuthorizationTest`, `AuthorizationServiceTest` passed. `CanonicalVitaCoraCareHistoryGuardTest` only checks that the projection source mentions `CARE_TRANSFER` and `CARE_CREATED`. |
| Professional private history isolation | PARTIAL | `M28Migration080StaticGuardsTest` checks RLS on `veterinary_professional_cares` and related tables (passed). No test asserts that a second professional cannot read another clinic's private care. Seed SQL mentions `care_private` without an assertion. |
| Private media | PARTIAL | `Sec03AbuseControlsContractTest`, `SocialAvatarAclMigrationTest`, `FileAuthorizationTest` passed. They read source and migrations. |
| Admin separation | COVERED at JVM | `AdminAccessPolicyTest` (person does not enter administration), `PersonUsernameLoginContractTest`, `AdminSessionRoutingTest` passed inside the suite. |
| Rate limits | PARTIAL | `Sec03AbuseControlsContractTest` asserts migration 1063 policies and `AuthErrorMapper` maps rate-limit copy. No live quota test. |
| Signed URLs | PARTIAL | Same Sec03 test plus `AndroidFinalClosureContractTest.signedUrlDoesNotRequireClientSelect` passed. Client must not call `createSignedUrl` or embed `service_role`. Edge function source is checked as text. No live signed-URL denial test. |

## P0 test gaps

- Linux Fast runner equivalent to `run-regression.ps1`, targeting `testLocalDebugUnitTest`.
- Deterministic Android unit tests (`Log`, `SystemClock`, image decode) so the baseline is not 41 environment failures.
- Configuration cache failure on Gradle 9.4.1, or Cloud builds must pass `--no-configuration-cache`.
- JVM contract for migration 1096 matching `search_path` (REG-BUG-003).
- JVM test for verification PENDING refresh after submit (REG-BUG-005).
- JVM or Compose-without-device test for profile switch back to Home (REG-BUG-006).
- Test that the Community form keeps field state when the map opens (REG-BUG-010).
- Assertion that professional private care is not readable across providers.
- `NewApi` on minSdk 26 (`URLDecoder.decode`, `LocationManager.isLocationEnabled`).

## P1 test gaps

- Retarget the 26 group-C tests and the 22 group-B source scans.
- Confirm or fix the 7 group-D failures with one cheap test each, then the code.
- QR direct navigation is Maestro-only; add a route/unit contract for `m14/pets/{petId}/share` if that remains the rule (payload is now HTTPS).
- Live RLS, rate limit, and signed-URL denial stay probes, not a gated task.
- `change-impact-map.yaml` repository and domain fallbacks have empty Gradle filters.
- Lint: Media3 opt-in, `StateFlow.value` in composition, unremembered state, `MissingTranslation` vs `values-es-rAR`.

## Product bugs found

Possible only. None were changed.

- In-memory adoption finalize does not archive the pet or transfer `ownerId`.
- Deceased pet edit does not lock mutations in `M08IntegrationRegressionTest`.
- Pet edit integration test never reaches `isEditMode`.
- `LegacyPetRepositoryAdapter` create/update profile and health call counts diverge from the atomic-create comments in the tests.
- `URLDecoder.decode(String, Charset)` and `LocationManager.isLocationEnabled` are above minSdk 26.
- Compose: `StateFlow.value` in `MyPetsScreen` and `ResponderBaseLocationScreen`; unremembered state in `GoogleLeoVerMap`.

Group A failures are not listed as product bugs.

## Files modified

- `docs/cloud/LEOVER-CLOUD-VALIDATION-01.md` (this report).

Not committed: `local.properties` (gitignored, `sdk.dir` only). `gradlew` mode was touched to run the wrapper and restored. Two VitaCora xlsx files showed a binary metadata diff with the same byte length; they were not committed and product code was not edited.

```
TEST IMPACT: audit only; no product behavior change
FAST/AFFECTED TESTS: FAIL
  app testLocalDebugUnitTest 2927/3023 PASS, 96 FAIL
  shared testAndroidHostTest 390/390 PASS
MAESTRO REQUIRED: NO
MAESTRO RUN: NOT RUN
FULL REGRESSION: NOT RUN
```

EMULATOR: NOT RUN
MAESTRO: NOT RUN
STAGING: NOT MODIFIED
PROD: NO
MAIN MODIFIED: NO
MERGE: NO

## Next recommended block

Cloud baseline repair, still without new features:

1. Bash Fast runner on `testLocalDebugUnitTest` + `--no-configuration-cache`.
2. Make the 41 environment failures deterministic (fake clock, log, image ingest) and re-run. Do not delete tests.
3. Add the missing P0 contracts: 1096 `search_path`, PENDING refresh, Community form/map state, professional private-care isolation.
4. Fix the 3 `NewApi` lint errors.
5. Only after that, retarget group B and C tests to current product rules.
