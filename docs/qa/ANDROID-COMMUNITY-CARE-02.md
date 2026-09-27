# ANDROID-COMMUNITY-CARE-02

Maps baseline: WORKING. Web: not built.

## FOUND/LOST hardening

- Size: `public.pets.size` (canonical). Neutral if UNKNOWN.
- Breed: `public.pets.breed_id`. Scoring +0.12 if both known and equal. Neutral otherwise.
- Age: same `PetBirth` / `estimated_age_months`. Scoring only. Not a hard filter.
- Waves: `pg_cron` `leover-lost-found-waves` every 1 minute (1090) → `canon_tick_lost_found_waves` only if `next_wave_at <= now()`. List RPC does not schedule.
- Matching 1091: threshold 0.55, cap 15, order score/distance/incident.
- Claim 1091: 2s `pg_sleep` arbitration, nearest wins, then CLAIMED.
- Owner LOST notified on auto-match. Custodian notified on OWNER_ASSERT. Owner can say no. Custodian can reject without closing cases.
- Claim = CLAIMED. Physical care = IN_CARE.
- Simultaneous claims: `lost_found_claim_attempts`, nearest distance, then created_at, then person id.

## Physical QA round 1 (2026-09-21)

- “Usar mi ubicación”: permission request + Fused + manual pin fallback.
- Required fields: `LeoRequiredField` ` *` + field errors.
- LOST/FOUND photo: shared camera + gallery picker.
- Publish: 1094 `LF-CREATE-*`; 1095 `_canon_geo_point` (root cause of LF-CREATE-UNKNOWN was `42883` `st_makepoint` with `search_path=public`).
- SHELTER tutorial: T10B only (not T10A veterinary); includes verificación + CTA.
- Verification: real `canon_request_leover_verification`; hydrates org/person `base_location`; address + “Usar mi ubicación”.

## APK

path: `apk/LeoVer-M08-Staging-debug.apk`

SHA-256: `07900767CB7A2944BC8AC93CE7782FDA910B80568101A174D20A646B5314EA40`

## Closure evidence (2026-09-20)

Matching V1 has **no score threshold and no candidate cap**. Same-species LOST all become candidates and all get notified. Proposed V1 (not applied): min score 0.55 + cap 15 + `ORDER BY score DESC, distance ASC NULLS LAST`.

`canon_claim_lost_found` locks the alert first (`FOR UPDATE`). The 8s nearest window only sees attempts already inserted inside that same locked transaction. First locker wins. Loser gets `ALERT_ALREADY_CLAIMED`.

Verification RPC does not check profile/contact/location/terms. Android does not call `canon_request_leover_verification`. Badges hidden (`VerificationDisplayPolicy.BADGE_VISIBLE = false`).

Vet invite persists `vet_pending_owner_invites` only. No email send. No `email_confirmed_at` auto-link.
