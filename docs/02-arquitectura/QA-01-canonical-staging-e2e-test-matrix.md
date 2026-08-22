# QA-01 — Canonical Staging E2E test matrix

Staging: `tobqbddfcyitwgbkthhy`. Legacy `wystsapjfpdtoprlmizz` is never a target.

QA auth users are admin-confirmed for domain/E2E. They are **not** proof of AUTH-01 signup/OTP.

`LEOVER_QA_PASSWORD` is required to seed. If absent: `QA_PASSWORD_REQUIRED`.

| Account | Username | Role |
|---------|----------|------|
| QA_PERSON_PUBLIC | `qa_public` | Public profile |
| QA_PERSON_PRIVATE | `qa_private` | Private profile |
| QA_RESCUER | `qa_rescuer` | Lost/Found |
| QA_ORG_ADMIN | `qa_org_admin` | Shelter org |
| QA_VET | `qa_vet` | Vet org/person |
| QA_PROVIDER | `qa_provider` | Walker |
| QA_DAYCARE | `qa_daycare` | Daycare/trainer |
| QA_SECOND_CONSUMER | `qa_second` | Approved follower of private |

Do not use `velu` as a QA-01 fixture.

## AUTOMATED

| ID | Precondition | Account | Steps | Expected | Result | Evidence |
|----|--------------|---------|-------|----------|--------|----------|
| AUTO-GEO-01 | Unit catalog with CABA aliases | n/a | Filter mock `Almagro, CABA` with `Ciudad Autónoma de Buenos Aires` | Match; BA fixture excluded | PASS | `ServiceLocationFilterTest` |
| AUTO-WIRE-01 | Source tree | n/a | DataProvider Staging path | `CanonicalServiceRepository`, no silent mock fallback | PASS | `Qa01StagingGuardsTest` |
| AUTO-QA-01 | Source tree | n/a | QA scripts mention Staging ref and password gate | Seed is not migration 1027 DML | PASS | `Qa01StagingGuardsTest` |

## INTEGRATION / REMOTE STAGING

Requires `qa01.ps1 -Action seed` (password + service_role locally). Until then: PENDING.

| ID | Precondition | Account | Steps | Expected | Result | Evidence |
|----|--------------|---------|-------|----------|--------|----------|
| INT-GEO-01 | Seeded providers | any confirmed | `canon_list_providers` | CABA + BA + Córdoba coverage ids present | PENDING | RPC after seed |
| INT-PET-01 | Seeded pets | `qa_public` | list pets / health | PET_C health rows exist | PENDING | SQL count |
| INT-SOC-01 | Seeded posts | `qa_public` | `canon_list_social_feed` | ≥1 QA post | PENDING | RPC |
| INT-SEC-01 | Seeded private person | stranger | `canon_get_public_person(qa_private)` | Limited / denied private fields | PENDING | RPC |

## PHYSICAL DEVICE

Install `apk/LeoVer-CANONICAL-STAGING-FULL-QA.apk`. Fresh signup uses a new email, not QA admin users.

| ID | Precondition | Account | Steps | Expected | Result | Evidence |
|----|--------------|---------|-------|----------|--------|----------|
| AUTH-01 | Confirm Email ON | new email | Signup + OTP 6–10 digits | Session + PERSON + onboarding/home | PENDING | Manual |
| AUTH-02 | Seeded | `qa_public` | Login | Home, no onboarding if complete | PENDING | Manual |
| ONB-01 | New PERSON incomplete | fresh signup | Complete username + locality | Home | PENDING | Manual |
| ONB-02 | Complete PERSON | `qa_public` | Second login | Skips onboarding | PENDING | Manual |
| PROFILE-01 | Seeded | any | Open `qa_public` | Public profile visible | PENDING | Manual |
| PROFILE-02 | Seeded | stranger | Open `qa_private` | Limited private profile | PENDING | Manual |
| PROFILE-03 | Seeded follows | `qa_rescuer` / `qa_private` | Request / accept / reject | Follow graph; no FRIEND | PENDING | Manual; Android follow repo still mock |
| GEO-01 | Canonical directory | any | Filter CABA | Only CABA-linked fixtures | PENDING until seed | Manual |
| GEO-02 | Canonical directory | any | Filter Buenos Aires | Only BA fixtures (San Vicente / Almirante Brown) | PENDING until seed | Manual |
| GEO-03 | Canonical directory | any | Province + locality | Reloads; no stale mix | PENDING until seed | Manual |
| SERVICE-01 | Canonical directory | any | Category chips | VET / WALKER / TRAINER filter; SHOP empty (no catalog code) | PENDING | Manual |
| SERVICE-02 | Canonical directory | any | Open provider | Public/basic data; back works | PENDING | Manual |
| PET-01 | Session | `qa_public` | Create pet | Canonical pet | PENDING | Manual |
| PET-02 | PET_A | `qa_public` | Edit + reload | Persists | PENDING | Manual |
| PET-03 | M05 | `qa_public` | Avatar upload | Reload without signed-URL authority | PENDING | Manual |
| PET-04 | PET_B | `qa_private` + `qa_second` | Two OWNERs + AUTHORIZED | Creator ≠ eternal authority | PENDING | Manual |
| VITA-01 | PET_C | `qa_public` | Open VitaCora | Composed from canonical domains | PENDING | Manual |
| HEALTH-01 | PET_C | `qa_public` | Add/reload record | Write+read; stranger denied | PENDING | Manual |
| LOST-01 | PET_E | `qa_rescuer` | Lost list/detail | Public contract; no exact coords | PENDING | Manual |
| FOUND-01 | PET_F | `qa_rescuer` | Found list/detail | Same | PENDING | Manual |
| ADOPT-01 | PET_G / PET_H | any | List + web | OPEN visible; HIDDEN safe 404 | PENDING | Manual |
| ORG-01 | Seeded orgs | `qa_org_admin` | Org + context if implemented | Membership not creator-ownership | PENDING | Manual |
| SOCIAL-01 | Seeded posts | `qa_public` | Feed / profile | Posts; no fabricated Reels | PENDING | Manual |
| MSG-01 | Seeded conversation | `qa_public` / `qa_second` | Open thread | Person↔person; org context if implemented | PENDING | Manual |
| WEB-01 | PET public_code | n/a | Public pet route | Safe public page | PENDING | Manual |
| WEB-02 | Adoption public_code | n/a | Public adoption | OPEN ok; HIDDEN 404 | PENDING | Manual |
| SEC-01 | Private pet/profile | stranger | Open restricted | Denied | PENDING | Manual |

## Visible Comunidad cards (pre-fix audit)

| DISPLAY_NAME | SOURCE | PROVINCE | LOCALITY |
|--------------|--------|----------|----------|
| Mock veterinarias / paseadores / tiendas / educadores (8 `MockData.serviceProfiles`) | MOCK | free-text `CABA` etc. | free-text |
| Canonical providers | CANONICAL after wiring | `province_ids` from coverage | `locality_ids` |

CABA filter was broken because the picker emits `Ciudad Autónoma de Buenos Aires` while mock cards say `CABA`, compared as raw substring. Consumer now uses catalog aliases + coverage IDs. Staging Comunidad no longer falls back to mocks when the backend list is empty.
