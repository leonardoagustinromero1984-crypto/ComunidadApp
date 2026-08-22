# UI-01 — Canonical LeoVer design system and full screen audit

Permanent product/engineering reference. Does **not** rewrite historical reports.

**Date:** 2026-08-16  
**Scope:** Android production UI after AUTH / ONB / RESCUE / UX-03 / CONTEXT-02 / COMMUNITY-02 / ORG-02.  
**Not in scope:** product architecture redesign, backend contract changes, new migrations (none required), emulator/screenshot tests.

---

## Development rule (permanent)

**NEW SCREEN:** must use canonical LeoVer theme and components from creation (`LeoVerTheme` tokens + `LeoVerScaffold` / Leo primitives).

**MODIFIED SCREEN:** must not regress an already canonical screen back to legacy styling.

**If touching a legacy screen substantially:** bring the touched portion into compliance.

Community’s current layout is protected. Do not restore generic tiles (`LeoServiceTile`) or drop category chips / “Cerca mío” / “Ver perfil” cards.

Future tutorials are **data/config + `LeoVerTutorialPager`**, not improvised layouts.

All operational contexts (Refugio, Veterinario, Paseador, Foster, Daycare, etc.) share the same LeoVer visual system. Tools and bottom destinations may change; background, type, cards, buttons, forms, feedback, and spacing must not become a separate product.

---

## UX-05 active direction (experimental)

Physical QA of UX-04 approved the near-white background and rejected sage/teal V2 primaries. Accent is original LeoVer green again, **not** marked as permanent brand law.

| Key | Value |
|---|---|
| HOME_CANONICAL | SOCIAL_FIRST |
| HOME_OPERATIONAL_SHORTCUTS | REMOVED |
| COMMUNITY_SEARCH_HIERARCHY | SERVICE → FILTERS → RESULTS |
| PROFILE_CONTEXT_SWITCHER | MOVED_TO_SETTINGS |
| PROFILE_ADD_FUNCTION | COMPACT_VISIBLE_ACTION |
| SETTINGS_CANONICAL_UI | REQUIRED |
| VISUAL_DIRECTION | V3_EXPERIMENTAL |
| BACKGROUND | `#FAFBF8` |
| PRIMARY_UI_GREEN | `#49B749` |
| PRIMARY_UI_GREEN_DARK | `#247A3D` |
| PRIMARY_UI_GREEN_SOFT | `#EEF8EE` |
| SAGE_PRIMARY | REMOVED (`#74AD7F`) |
| TEAL_PRIMARY | REMOVED (`#6D9FA1`) |

Green usage: selected states, small icons, small CTAs, active nav, brand actions. No large green headers/cards. Orange remains scarce (`+ Publicar`). Red is semantic only.

`Color.kt` `BrandBackground = #FAFBF8` is the canonical app background. UI contract enforcement is **mandatory**.

Inicio is stories then feed. Comunidad does not fetch mixed providers until a service category is selected. Profile no longer lists “Usar LeoVer como”; that switcher lives in Configuración.

---

## Canonical visual tokens

Source of truth: `app/src/main/java/com/comunidapp/app/ui/theme/Color.kt`  
Documented API: `LeoVerTheme` in `LeoVerTheme.kt`

| Token | Hex | Kotlin |
|---|---|---|
| GENERAL_BACKGROUND | `#FAFBF8` | `LeoVerTheme.colors.background` / `BrandBackground` |
| SURFACE | `#FFFFFF` | `LeoVerTheme.colors.surface` / `BrandWhite` |
| BRAND_ORANGE | `#FF7A00` | `LeoVerTheme.colors.brandOrange` / `BrandOrange` |
| BRAND_GREEN | `#49B749` | `LeoVerTheme.colors.brandGreen` / `BrandGreen` |
| PROFILE_GREEN | `#66B978` | `LeoVerTheme.colors.profileGreen` / `ProfileGreen` |
| SOFT_CREAM | `#FFF8E1` | `LeoVerTheme.colors.softCream` / `BrandCream` |
| TEXT_PRIMARY | `#333333` | `LeoVerTheme.colors.textPrimary` / `BrandText` |

Rules:

- Full-screen default is `#FFFDF8`. Cream is an **accent** only (chips, badges, highlights).
- Do **not** globally replace `BrandGreen` with `ProfileGreen`. Profile green is profile/context-specific (including Community selected chips).
- Logo, launcher, and splash are unchanged.
- Semantic error / warning / success stay separate (`UrgentRed`, `WarningAmber`, `SuccessGreen`).
- Production screens should write `LeoVerTheme.colors.background`, not `Color(0xFFFFFDF8)`.

Material `ComunidappTheme` maps `background → BrandBackground`, `surface → BrandWhite`, `surfaceVariant → BrandCream`.

---

## Spacing

`LeoDimens` canonical scale (aliases added in UI-01):

| Role | dp | Token |
|---|---|---|
| XS | 4 | `LeoVerTheme.spacing.xs` / `LeoDimens.SpaceXs` |
| S | 8 | `SpaceS` |
| M | 12 | `SpaceM` |
| L | 16 | `SpaceL` |
| XL | 24 | `SpaceXl` |
| XXL | 32 | `SpaceXxl` |

Do not blindly replace a component that legitimately needs another dimension.

---

## Shape

| Role | dp | Token |
|---|---|---|
| Small / chip | 12 | `LeoVerTheme.shapes.small` / `RadiusChip` |
| Card | 16 | `shapes.card` / `RadiusCard` |
| Large / container | 22 | `shapes.large` / `RadiusLarge` |
| Pill | full | `shapes.pill` / `RadiusPill` |
| Field | 14 | `RadiusField` |

`RadiusCardFeature` now aliases `RadiusLarge` (22dp).

---

## Typography roles

| Role | Style |
|---|---|
| Screen title | `LeoVerTheme.typography.screenTitle` / `LeoPageTitle` |
| Section title | `LeoSectionTitle` |
| Card title | `LeoCardTitle` |
| Body | `LeoBody` |
| Secondary body | `LeoSecondary` |
| Caption | `LeoCaption` |
| Button | `LeoButton` |
| Chip | `LeoChip` |

---

## Canonical components (reuse, not a second library)

Existing Leo / V2 primitives remain the implementation. UI-01 adds **names + a few missing pieces**, not a parallel kit.

| Canonical name | Implementation |
|---|---|
| LeoVerScaffold / LeoVerScreen | `LeoVerScaffold` |
| LeoVerTopBar | `LeoTopAppBar` |
| LeoVerCard | `LeoCard` / `V2SurfaceCard` |
| LeoVerPrimaryButton | `LeoPrimaryButton` |
| LeoVerSecondaryButton | `LeoSecondaryButton` |
| LeoVerChip / LeoVerFilterChip | `LeoFilterChip` |
| LeoVerTextField | `LeoTextField` |
| LeoVerSelector | `HealthOptionDropdown` |
| LeoVerSectionHeader | `LeoSectionHeader` / `V2SectionHeader` |
| LeoVerProfileHeader | `LeoGreetingHeader` |
| LeoVerProviderCard | `LeoVerProviderCard` (Community) |
| LeoVerEmptyState | `LeoEmptyState` |
| LeoVerErrorState | `ErrorState` + `CanonicalUiErrorMapper` |
| LeoVerLoadingState | `LoadingState` |
| LeoVerTutorialPage | `LeoVerTutorialPager` |
| LeoVerBottomNavigation | `ComunidappBottomBar` + `ContextNavigation` |
| Forms | `V2FormScaffold` (IME, scroll, error banner, submitting) |

Deprecated / do not restore on Community: `LeoServiceTile`.  
Legacy / do not use as authority: `AccountTypeDropdown` (AccountType/AppMode).

**Canonical component count (named public API): 22**

---

## Screen contract

Every production screen must handle, as applicable:

- Normal / loading / empty / error
- Forms: validation, disabled/submitting, friendly backend errors, keyboard/IME, scroll
- No raw SQL, PostgREST, constraint names, stack traces, or technical enum names
- Background `#FFFDF8`, surfaces `#FFFFFF`
- Correct back + context bottom navigation
- Geographic copy: provincia / localidad (not ad-hoc country ISO in user-facing org/foster forms)

Friendly errors: `CanonicalUiErrorMapper` + `CanonicalUiErrorKind`

| Kind | Typical trigger |
|---|---|
| VALIDATION | invalid input |
| ALREADY_EXISTS | unique / slug / duplicate |
| PERMISSION | 403 / RLS |
| NETWORK | timeout / unknown host |
| SERVER | 5xx |
| NOT_FOUND | 404 |
| CONFLICT | 409 |
| UNKNOWN | fallback copy |

Raw detail may appear only in sanitized debug logs (`sanitizedDiagnostic`).

---

## Community protection

`ComunidadScreen` + `LeoVerProviderCard` are the canonical Community UI:

- Pilot background `#FAFBF8` (V3 experimental)
- Title + subtitle, then **service categories first**
- Filters (Provincia / Localidad / Cerca mío / tags if present) only after a category is selected
- Results after filters: thumbnail, category, optional rating, locality, **Ver perfil**
- Personal bottom nav includes Comunidad
- Empty backend stays empty (no mock fallback on Staging)
- Selected chips use V3 `leoVisual().primary` (`#49B749`), not sage/teal and not giant green blocks

Regression tests: `UiRegressionGateTest`, `LeoVerUx07ContractTest`, `LeoVerMapPolicyGateTest`, `PersonaBottomSurfacesTest`, `CommunityCanonicalUiTest`, `Ux05SocialHomeCommunityProfileSettingsTest`.

`UI_CONTRACT_ENFORCEMENT = MANDATORY`. Migrated Community cannot import legacy UI packages or cream/sage tiles.

---

## Tutorials

`LeoVerTutorialPager` is the canonical renderer. `TutorialPagerScreen` is a compatibility alias. Tutorial content lives in `TutorialCatalog` (data), not custom screens.

---

## Contextual UI

`ContextNavigation` changes labels/routes (Inicio / Animales / Gestión / Tránsitos / Agenda…) but always:

- 5 destinations
- Inicio + Perfil bookends
- Prominent Publicar
- Same `ComunidappBottomBar` chrome (`BrandWhite` bar, LeoVer colors)

---

## UI regression gate

No emulator. No screenshots.

```
powershell -File scripts/ui-regression-gate.ps1
```

or:

```
gradlew :app:uiRegressionGate --no-configuration-cache
```

(`uiRegressionGate` runs unit tests; the script is the **focused** filter set.)

Detects: legacy `#FFF6EA`, `LeoServiceTile` in screens, banned technical strings, `AccountTypeDropdown` in screens, `MockData.` in screens, Community category-first layout, Home social-first (no operational shortcuts), Settings grouped rows, no sage/teal V2 primary regression, tutorial pager contract, cream used as top-bar/full-screen fill.

**Future visual testing hook:** add Compose screenshot / golden tests under CI (macOS/Linux) later. Do not require them on this Windows development machine. This static gate stays the local requirement.

Permanent checklist: [UI-01-screen-definition-of-done.md](./UI-01-screen-definition-of-done.md)

---

## Legacy inventory (UI-01 snapshot)

| Finding | Count / notes |
|---|---|
| Old full-screen hex `#FFF6EA` | **0** in production UI (previews migrated to `#FFFDF8`) |
| Scaffold / V2 full-screen cream | **0** (`LeoTopAppBar` and `V2ScreenBackground` now `BrandBackground`) |
| `BrandCream` still referenced | Accent + some unused imports across screens/components (legitimate accent in profile/home/tutorial) |
| `LeoServiceTile` | Definition only (deprecated). **0** screen call-sites |
| `AccountTypeDropdown` | Definition only. **0** screen call-sites |
| Screens bypassing Leo chrome | Module packs `m13`–`m28`, several admin/observability hosts |
| Runtime `MockData.` in screens | **0** |
| DataProvider `else -> Mock*` | Flavor fallback when canonical/Supabase is off — **not** Staging Community path |
| Technical strings in `ui/screens` | **0** (`SQLSTATE` / `PostgREST` / `duplicate key`) |

---

## Audit method

- Enumerated every `fun *Screen(` under `ui/screens` (**280** composables).
- Cross-checked `NavRoutes.kt` (**295** route constants excluding `ARG_*`).
- **PASS** is reserved for screens that materially match the LeoVer contract (not merely compiling). Uncertain → **NEEDS_POLISH**.
- Module packs (M13–M28) default to **LEGACY** even when they use `BrandBackground`.
- **BROKEN** = none identified at source level (no emulator run).
- **NOT_IMPLEMENTED** = no missing composable destinations; some refuge extras show in-product “not in this version” copy (counted as NEEDS_POLISH / existing screen).

### Totals

| STATUS | Count |
|---|---|
| PASS | 12 |
| NEEDS_POLISH | 143 |
| LEGACY | 125 |
| BROKEN | 0 |
| NOT_IMPLEMENTED | 0 |
| **Total production screens** | **280** |

PASS (material contract): `ComunidadScreen`, `HomeScreen`, `ProfileScreen`, `SumateScreen`, `PublishScreen`, `Onb02HostScreen`, `TutorialPagerScreen`, `FunctionSelectorScreen`, `UseLeoverAsScreen`, `HelpTutorialsScreen`, `FirstRunOnboardingScreen`, `FosterHomeFormScreen`.

ROUTE for each destination: `NavRoutes.kt` + `ComunidappNavGraph.kt` / `M16NavGraph.kt` / other module graphs. NAVIGATION: `ComunidappBottomBar` + `ContextNavigation` unless the destination is a pushed detail (back via `LeoTopAppBar`).

PRIMARY COMPONENTS, LOADING, EMPTY, ERROR, LEGACY_STYLE, ACTIVE_MOCK: per-row below. Domain = folder under `ui/screens`.

---

## Complete screen inventory

Columns: SCREEN/COMPOSABLE, FILE (under `ui/screens`), BACKGROUND, TOP BAR, LOADING, EMPTY, ERROR, LEGACY_STYLE, MOCK, STATUS.

| SCREEN/COMPOSABLE | FILE | BACKGROUND | TOP BAR | LOADING | EMPTY | ERROR | LEGACY_STYLE | MOCK | STATUS |
|---|---|---|---|---|---|---|---|---|---|
| AdministrativeAuditScreen | admin/AdministrativeAuditScreen.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| AdministrativeOperationsHubScreen | admin/AdministrativeOperationsHubScreen.kt | BrandBackground | LeoTopAppBar | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| AdminModerationScreen | admin/AdminModerationScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| LocationCatalogAdminScreen | admin/LocationCatalogAdminScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| ObservabilityOverviewScreen | admin/ObservabilityScreens.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| ObservabilityMetricsScreen | admin/ObservabilityScreens.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| ObservabilityHealthScreen | admin/ObservabilityScreens.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| ObservabilityIncidentsScreen | admin/ObservabilityScreens.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| ObservabilityRetentionScreen | admin/ObservabilityScreens.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| ObservabilityPermissionsInfoScreen | admin/ObservabilityScreens.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| ObservabilityAuditListScreen | admin/ObservabilityScreens.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| ObservabilityErrorsListScreen | admin/ObservabilityScreens.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| ObservabilityExportsScreen | admin/ObservabilityScreens.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| PlatformAdminScreen | admin/PlatformAdminScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| AdoptionApplyScreen | adoptions/AdoptionApplicationScreens.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| MyAdoptionApplicationsScreen | adoptions/AdoptionApplicationScreens.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| ReceivedAdoptionApplicationsScreen | adoptions/AdoptionApplicationScreens.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| AdoptionApplicationDetailScreen | adoptions/AdoptionApplicationScreens.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| AdoptionProcessScreen | adoptions/AdoptionCompletionScreens.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| AdoptionInterviewsScreen | adoptions/AdoptionCompletionScreens.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| AdoptionInterviewDetailScreen | adoptions/AdoptionCompletionScreens.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| AdoptionDocumentsScreen | adoptions/AdoptionCompletionScreens.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| AdoptionAgreementScreen | adoptions/AdoptionCompletionScreens.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| AdoptionFinalizeScreen | adoptions/AdoptionCompletionScreens.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| AdoptionFollowUpScreen | adoptions/AdoptionCompletionScreens.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| AdoptionFollowUpCheckDetailScreen | adoptions/AdoptionCompletionScreens.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| AdoptionDetailScreen | adoptions/AdoptionDetailScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| MyAdoptionsScreen | adoptions/AdoptionDetailScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| AdoptionFormScreen | adoptions/AdoptionFormScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| AdoptionsScreen | adoptions/AdoptionsScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| MiNegocioScreen | business/MiNegocioScreen.kt | BrandBackground | LeoTopAppBar | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| ChatListScreen | chat/ChatScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| ChatStartScreen | chat/ChatScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| ChatThreadScreen | chat/ChatScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| ComunidadScreen | comunidad/ComunidadScreen.kt | BrandBackground | LeoTopAppBar | YES | YES | PARTIAL/NO | NO | NO | PASS |
| ServiceDetailScreen | comunidad/ServiceDetailScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| FosterPlacementManagementScreen | foster/FosterCareManagementScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| FosterExpensesScreen | foster/FosterCareManagementScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| FosterExpenseFormScreen | foster/FosterCareManagementScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| FosterEvolutionScreen | foster/FosterCareManagementScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| FosterEvolutionFormScreen | foster/FosterCareManagementScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| FosterHelpScreen | foster/FosterCareManagementScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| FosterHelpFormScreen | foster/FosterCareManagementScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| FosterHelpDetailScreen | foster/FosterCareManagementScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| FosterCompleteScreen | foster/FosterCareManagementScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| FosterHistoryScreen | foster/FosterCareManagementScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| FosterHomesScreen | foster/FosterScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| MyFosterHomeScreen | foster/FosterScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| FosterHomeFormScreen | foster/FosterScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | PASS |
| FosterHomeDetailScreen | foster/FosterScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| FosterRequestFormScreen | foster/FosterScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| FosterRequestsScreen | foster/FosterScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| FosterRequestDetailScreen | foster/FosterScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| FosterPlacementsScreen | foster/FosterScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| FosterPlacementDetailScreen | foster/FosterScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| HomeScreen | home/HomeScreen.kt | BrandBackground | custom/none | PARTIAL/NO | YES | PARTIAL/NO | NO | NO | PASS |
| TermsDraftScreen | legal/LegalDraftScreens.kt | BrandBackground | LeoTopAppBar | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| PrivacyDraftScreen | legal/LegalDraftScreens.kt | BrandBackground | LeoTopAppBar | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| LegalDraftScreen | legal/LegalDraftScreens.kt | BrandBackground | LeoTopAppBar | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| ForgotPasswordScreen | login/ForgotPasswordScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| EmailVerificationScreen | login/ForgotPasswordScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| LoginScreen | login/LoginScreen.kt | BrandBackground | custom/none | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| RegisterScreen | login/RegisterScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| LostFoundMapScreen | lostfound/AlertMapScreen.kt | BrandBackground | LeoTopAppBar | YES | YES | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| LostFoundDetailScreen | lostfound/AlertMapScreen.kt | BrandBackground | LeoTopAppBar | YES | YES | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| LostFoundScreen | lostfound/LostFoundScreen.kt | BrandBackground | LeoTopAppBar | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| M13SightingListScreen | m13/M13SightingScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M13SightingCreateScreen | m13/M13SightingScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M13SightingDetailScreen | m13/M13SightingScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M13CaseMatchesScreen | m13/M13SightingScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M13MatchDetailScreen | m13/M13SightingScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M13MetricsScreen | m13/M13SightingScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M14ManagedVerificationsScreen | m14/M14Block3Screens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M14VerificationDetailScreen | m14/M14Block3Screens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M14IssueVerifiedCredentialScreen | m14/M14Block3Screens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M14RevokeCredentialScreen | m14/M14Block3Screens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M14PassportShareScreen | m14/M14Block3Screens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M14PassportHistoryScreen | m14/M14Block3Screens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M14PassportListScreen | m14/M14PassportScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M14PetPassportScreen | m14/M14PassportScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M14PassportEditScreen | m14/M14PassportScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M14CredentialsScreen | m14/M14PassportScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M14CredentialCreateScreen | m14/M14PassportScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M14CredentialDetailScreen | m14/M14PassportScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M14VerificationPrepScreen | m14/M14PassportScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M14PublicPassportScreen | m14/M14PassportScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M15FosterHubScreen | m15/M15FosterScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M15FosterHomesListScreen | m15/M15FosterScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M15FosterHomeDetailScreen | m15/M15FosterScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M15MyFosterHomeScreen | m15/M15FosterScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M15FosterRequestFormScreen | m15/M15FosterScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M15FosterRequestsScreen | m15/M15FosterScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M15PlacementsListScreen | m15/M15LifecycleScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | PARTIAL/NO | NO | NO | LEGACY |
| M15PlacementDetailScreen | m15/M15LifecycleScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | PARTIAL/NO | NO | NO | LEGACY |
| M15EvolutionListScreen | m15/M15LifecycleScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | PARTIAL/NO | NO | NO | LEGACY |
| M15EvolutionFormScreen | m15/M15LifecycleScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | PARTIAL/NO | NO | NO | LEGACY |
| M15DischargeScreen | m15/M15LifecycleScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | PARTIAL/NO | NO | NO | LEGACY |
| M15ExpensesScreen | m15/M15LifecycleScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | PARTIAL/NO | NO | NO | LEGACY |
| M15ExpenseFormScreen | m15/M15LifecycleScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | PARTIAL/NO | NO | NO | LEGACY |
| M15HelpListScreen | m15/M15LifecycleScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | PARTIAL/NO | NO | NO | LEGACY |
| M15HelpFormScreen | m15/M15LifecycleScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | PARTIAL/NO | NO | NO | LEGACY |
| M15OperationsScreen | m15/M15OperationsScreens.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | YES | NO | NO | LEGACY |
| M16SheltersListScreen | m16/M16ShelterScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M16ShelterDetailScreen | m16/M16ShelterScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M16ShelterManageScreen | m16/M16ShelterScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M17CampaignsListScreen | m17/M17DonationScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M17CampaignDetailScreen | m17/M17DonationScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M17CampaignManageScreen | m17/M17DonationScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M17CampaignEditScreen | m17/M17DonationScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M17HubScreen | m17/M17ExtendedScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M18EventsListScreen | m18/M18EventScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M18EventDetailScreen | m18/M18EventScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M18EventOperationsScreen | m18/M18EventScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M18EventManageScreen | m18/M18EventScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M18EventEditScreen | m18/M18EventScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M19SocialFeedScreen | m19/M19SocialScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M19PostDetailScreen | m19/M19SocialScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M19PostsManageScreen | m19/M19SocialScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M19PostEditScreen | m19/M19SocialScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M20ConversationListScreen | m20/M20MessagingScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M20ThreadScreen | m20/M20MessagingScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M21HubScreen | m21/M21ReputationScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M21SubjectScreen | m21/M21ReputationScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M21ReviewDetailScreen | m21/M21ReputationScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M21ReviewsScreen | m21/M21ReputationScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M21VerificationsScreen | m21/M21ReputationScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M22HubScreen | m22/M22ProviderScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M22CatalogScreen | m22/M22ProviderScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M22ProviderDetailScreen | m22/M22ProviderScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M22ManageScreen | m22/M22ProviderScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M23HomeScreen | m23/M23BookingScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M23AvailabilityScreen | m23/M23BookingScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M23MyBookingsScreen | m23/M23BookingScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M23BookingDetailScreen | m23/M23BookingScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M23ManageScreen | m23/M23BookingScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M23ManageCalendarScreen | m23/M23BookingScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M23ManageBookingsScreen | m23/M23BookingScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M25HubScreen | m25/M25MarketplaceScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M25CatalogScreen | m25/M25MarketplaceScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M25ShopDetailScreen | m25/M25MarketplaceScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M25CartScreen | m25/M25MarketplaceScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M25OrdersScreen | m25/M25MarketplaceScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M25ManageScreen | m25/M25MarketplaceScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M25MerchantOrdersScreen | m25/M25MarketplaceScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M25OrderDetailScreen | m25/M25MarketplaceScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M26HubScreen | m26/M26AiScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M26VisualMatchingScreen | m26/M26AiScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M26DuplicatesScreen | m26/M26AiScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M26AssistanceScreen | m26/M26AiScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M26RecommendationsScreen | m26/M26AiScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M26HistoryScreen | m26/M26AiScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M26ReviewQueueScreen | m26/M26AiScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M27HubScreen | m27/M27IntegrationScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M27WebhooksScreen | m27/M27IntegrationScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M27OAuthScreen | m27/M27IntegrationScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M27ApiKeysScreen | m27/M27IntegrationScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M27ContractsScreen | m27/M27IntegrationScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M27RateLimitsScreen | m27/M27IntegrationScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M27AppsScreen | m27/M27IntegrationScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M27DeliveriesScreen | m27/M27IntegrationScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M27AuditScreen | m27/M27IntegrationScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M28PetGrantsScreen | m28/M28Screens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M28PassportProposalsScreen | m28/M28Screens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| M28ClinicCareScreen | m28/M28Screens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | LEGACY |
| ModerationAppealDetailScreen | moderation/ModerationAppealDetailScreen.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| ModerationAppealQueueScreen | moderation/ModerationAppealQueueScreen.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| ModerationCaseDetailScreen | moderation/ModerationCaseDetailScreen.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| ModerationCaseQueueScreen | moderation/ModerationCaseQueueScreen.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| ModerationQueueScreen | moderation/ModerationQueueScreen.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| ModerationReportDetailScreen | moderation/ModerationReportDetailScreen.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| MyModerationAppealsScreen | moderation/MyModerationAppealsScreen.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| FirstRunOnboardingScreen | onboarding/FirstRunOnboardingScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | PASS |
| ProfileOnboardingScreen | onboarding/ProfileOnboardingScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| Onb02HostScreen | onboarding/onb02/Onb02Screens.kt | BrandBackground | LeoTopAppBar | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | PASS |
| TutorialPagerScreen | onboarding/onb02/Onb02Screens.kt | BrandBackground | LeoTopAppBar | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | PASS |
| FunctionSelectorScreen | onboarding/onb02/Onb02Screens.kt | BrandBackground | LeoTopAppBar | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | PASS |
| OrganizationFunctionSetupScreen | onboarding/onb02/Onb02Screens.kt | BrandBackground | LeoTopAppBar | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| UseLeoverAsScreen | onboarding/onb02/Onb02Screens.kt | BrandBackground | LeoTopAppBar | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | PASS |
| HelpTutorialsScreen | onboarding/onb02/Onb02Screens.kt | BrandBackground | LeoTopAppBar | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | PASS |
| CreateOrganizationScreen | organization/CreateOrganizationScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| EditOrganizationScreen | organization/EditOrganizationScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| MyOrganizationsScreen | organization/MyOrganizationsScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| OrganizationBranchesScreen | organization/OrganizationBranchesScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| OrganizationManageScreen | organization/OrganizationManageScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| OrganizationTeamScreen | organization/OrganizationTeamScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| PublicOrganizationScreen | organization/PublicOrganizationScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| MyPetsScreen | pets/MyPetsScreen.kt | BrandBackground | LeoTopAppBar | PARTIAL/NO | YES | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| PetAuthorizationsScreen | pets/PetAuthorizationsScreen.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| PetDetailScreen | pets/PetDetailScreen.kt | UNKNOWN/LEGACY | custom/none | YES | YES | YES | NO | NO | LEGACY |
| AddPetScreen | pets/PetFormScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| EditPetScreen | pets/PetFormScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| PetFormScreen | pets/PetFormScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| PetResponsibilitiesScreen | pets/PetResponsibilitiesScreen.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| PetStatusHistoryScreen | pets/PetStatusHistoryScreen.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| PetTransferDetailScreen | pets/PetTransferDetailScreen.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| PetTransfersScreen | pets/PetTransfersScreen.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| EditProfileScreen | profile/EditProfileScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| FriendRequestsScreen | profile/FriendRequestsScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| MyPublicationsScreen | profile/MyPublicationsScreen.kt | BrandBackground | LeoTopAppBar | PARTIAL/NO | YES | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| NotificationPreferencesScreen | profile/NotificationPreferencesScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| NotificationsScreen | profile/NotificationsScreen.kt | BrandBackground | LeoTopAppBar | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| ProfileScreen | profile/ProfileScreen.kt | BrandBackground | custom/none | YES | YES | PARTIAL/NO | NO | NO | PASS |
| SearchFriendsScreen | profile/SearchFriendsScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| UserPublicProfileScreen | profile/UserPublicProfileScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| PublishGeneralScreen | publish/PublishForms.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | YES | NO | NO | NEEDS_POLISH |
| PublishUrgentScreen | publish/PublishForms.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | YES | NO | NO | NEEDS_POLISH |
| PublishReelScreen | publish/PublishForms.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | YES | NO | NO | NEEDS_POLISH |
| PublishStoryScreen | publish/PublishForms.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | YES | NO | NO | NEEDS_POLISH |
| PublishQuestionScreen | publish/PublishForms.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | YES | NO | NO | NEEDS_POLISH |
| PublishPromoScreen | publish/PublishForms.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | YES | NO | NO | NEEDS_POLISH |
| PublishFeedTypeScreen | publish/PublishForms.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | YES | NO | NO | NEEDS_POLISH |
| PublishAdoptionScreen | publish/PublishForms.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | YES | NO | NO | NEEDS_POLISH |
| PublishLostFoundScreen | publish/PublishForms.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | YES | NO | NO | NEEDS_POLISH |
| PublishFosterScreen | publish/PublishForms.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | YES | NO | NO | NEEDS_POLISH |
| PublishEventScreen | publish/PublishForms.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | YES | NO | NO | NEEDS_POLISH |
| PublishDonationScreen | publish/PublishForms.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | YES | NO | NO | NEEDS_POLISH |
| PublishShelterScreen | publish/PublishForms.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | YES | NO | NO | NEEDS_POLISH |
| PublishScreen | publish/PublishScreen.kt | BrandBackground | LeoTopAppBar | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | PASS |
| SearchScreen | search/SearchScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| AccountAccessBlockedScreen | security/AccountAccessBlockedScreen.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| AccountSecurityScreen | security/AccountSecurityScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| PasswordResetActiveScreen | security/AccountSecurityScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| LegalConsentRequiredScreen | security/AccountSecurityScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| ShelterPublicCampaignsScreen | shelters/ShelterCampaignsAndAidScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterCampaignsScreen | shelters/ShelterCampaignsAndAidScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterCampaignDetailScreen | shelters/ShelterCampaignsAndAidScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterCampaignFormScreen | shelters/ShelterCampaignsAndAidScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterCampaignUpdateScreen | shelters/ShelterCampaignsAndAidScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterPublicSupplyRequestsScreen | shelters/ShelterCampaignsAndAidScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterSupplyRequestsScreen | shelters/ShelterCampaignsAndAidScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterSupplyRequestDetailScreen | shelters/ShelterCampaignsAndAidScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterSupplyRequestFormScreen | shelters/ShelterCampaignsAndAidScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterSupplyContributeScreen | shelters/ShelterCampaignsAndAidScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterSupplyContributionsScreen | shelters/ShelterCampaignsAndAidScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterDetailScreen | shelters/ShelterDetailScreen.kt | BrandBackground | LeoTopAppBar | YES | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| ShelterPublicEmergenciesScreen | shelters/ShelterEmergenciesEventsReportsScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterEmergenciesScreen | shelters/ShelterEmergenciesEventsReportsScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterEmergencyDetailScreen | shelters/ShelterEmergenciesEventsReportsScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterEmergencyFormScreen | shelters/ShelterEmergenciesEventsReportsScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterPublicEventsScreen | shelters/ShelterEmergenciesEventsReportsScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterEventsScreen | shelters/ShelterEmergenciesEventsReportsScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterEventDetailScreen | shelters/ShelterEmergenciesEventsReportsScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterEventFormScreen | shelters/ShelterEmergenciesEventsReportsScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterEventRegistrationsScreen | shelters/ShelterEmergenciesEventsReportsScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterReportsScreen | shelters/ShelterEmergenciesEventsReportsScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterOpsListScreen | shelters/ShelterOperationsScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| MySheltersScreen | shelters/ShelterOperationsScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterOpsFormScreen | shelters/ShelterOperationsScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterOpsDetailScreen | shelters/ShelterOperationsScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterDashboardScreen | shelters/ShelterOperationsScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterOpsPetsScreen | shelters/ShelterOperationsScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterIntakeScreen | shelters/ShelterOperationsScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterOpsPetDetailScreen | shelters/ShelterOperationsScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterOpsVolunteersScreen | shelters/ShelterOperationsScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ShelterVolunteerInviteScreen | shelters/ShelterOperationsScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| SheltersScreen | shelters/SheltersScreen.kt | BrandBackground | LeoTopAppBar | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | NEEDS_POLISH |
| SumateScreen | sumate/SumateScreen.kt | BrandBackground | LeoTopAppBar | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | PASS |
| CreateSupportTicketScreen | support/CreateSupportTicketScreen.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| MySupportTicketsScreen | support/MySupportTicketsScreen.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| SupportQueueScreen | support/SupportQueueScreen.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| SupportTicketAdminDetailScreen | support/SupportTicketAdminDetailScreen.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| SupportTicketDetailScreen | support/SupportTicketDetailScreen.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| M16ShelterVerificationReviewScreen | verification/M16ShelterVerificationReviewScreen.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| OrganizationVerificationQueueScreen | verification/OrganizationVerificationQueueScreen.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| OrganizationVerificationReviewScreen | verification/OrganizationVerificationReviewScreen.kt | UNKNOWN/LEGACY | custom/none | PARTIAL/NO | PARTIAL/NO | PARTIAL/NO | NO | NO | LEGACY |
| VeterinaryBookAppointmentScreen | veterinary/VeterinaryAppointmentScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| MyVeterinaryAppointmentsScreen | veterinary/VeterinaryAppointmentScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| VeterinaryAppointmentDetailScreen | veterinary/VeterinaryAppointmentScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| VeterinaryManagedAgendaScreen | veterinary/VeterinaryAppointmentScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| VeterinaryAppointmentManagementScreen | veterinary/VeterinaryAppointmentScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| VeterinaryScheduleSettingsScreen | veterinary/VeterinaryAppointmentScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| VeterinaryAvailabilityRulesScreen | veterinary/VeterinaryAppointmentScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| VeterinaryClinicProfessionalsScreen | veterinary/VeterinaryManageScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| VeterinaryClinicServicesScreen | veterinary/VeterinaryManageScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| VeterinaryClinicHoursScreen | veterinary/VeterinaryManageScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| VeterinaryClinicManageHubScreen | veterinary/VeterinaryManageScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| VeterinaryDirectoryScreen | veterinary/VeterinaryScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| VeterinaryClinicDetailScreen | veterinary/VeterinaryScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| ManagedVeterinaryClinicsScreen | veterinary/VeterinaryScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |
| VeterinaryClinicDraftScreen | veterinary/VeterinaryScreens.kt | BrandBackground | LeoTopAppBar | YES | YES | YES | NO | NO | NEEDS_POLISH |


---

## Next polish batch (not blocking the system)

Priority after this gate:

1. Login / Register visual alignment to `LeoVerScaffold` + `LeoTextField`
2. Service detail / org create / pet form field density
3. M13–M28 module hosts: wrap with `LeoVerScaffold` when those modules are next touched
4. Remove unused `BrandCream` imports left after the background migration

No backend migration was required for UI-01.
