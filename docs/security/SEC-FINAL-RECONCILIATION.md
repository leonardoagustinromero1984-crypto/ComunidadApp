# SEC-FINAL — Reconciliación SEC-01 … SEC-05

Proyecto STAGING: `tobqbddfcyitwgbkthhy`.  
Fecha de evidencia: 2026-09-02 (controles) / 2026-09-04 (SEC-04C drill).  
`wystsapjfpdtoprlmizz`: **SEC-04 RESTORE TEST** (no Android, no STAGING).

Migraciones de seguridad aplicadas (no editadas):

| version | name |
|---|---|
| 20260902140000 | 1061_sec01_p0_hardening.sql |
| 20260902180000 | 1062_admin_mfa_aal2.sql |
| 20260902200000 | 1063_sec03_rate_limits_quotas.sql |
| 20260902210000 | 1064_sec05_authz_gaps.sql |
| 20260902220000 | 1065_sec_final_least_privilege.sql |
| 20260904180000 | 1066_sec_p1_closure.sql |
| 20260904220000 | 1067_sec_p1_signed_url_window.sql |

## Inventario

| ID | Hallazgo | Origen | Estado | Severidad |
|---|---|---|---|---|
| SEC01-P0 | Hardening RLS/RPC/grants, service_role fuera de Android, throttle admin login | SEC-01 / 1061 | FIXED | P0 cerrado |
| SEC02-P0 | MFA nativo + AAL2 staff | SEC-02 / 1062 | FIXED | P0 cerrado |
| SEC02-AAL1 | `get_admin_auth_state` / `has_permission` / `admin_begin_login` en AAL1 | SEC-02 | NOT APPLICABLE | routing / login |
| SEC02-PWD | `admin_clear_must_change_password` sin AAL2 | SEC-FINAL | NOT APPLICABLE | self-only; flujo password-before-MFA |
| SEC03-LIMITS | Rate limits / quotas / kill switches | SEC-03 / 1063 | FIXED (STAGING) | ver propuesta PROD |
| SEC03-SIGNED | SDK `createSignedUrl` puede omitir throttle canónico | SEC-03/05 | FIXED | 1066 Edge + sin SELECT privado |
| SEC03-FEED | `canon_list_social_feed` jsonb sin cursor | SEC-03/05 | FIXED | 1066 limit 30 / cursor `(created_at, id)` |
| SEC03-MSG | `canon_list_messages` jsonb sin cursor | SEC-03/05 | FIXED | 1066 recientes 50 / cursor `(created_at, id)` |
| SEC04-BACKUP | Scheduled backups | SEC-04 | STAGING Free = N/A; PROD Pro REQUIRED | evidencia Leonardo |
| SEC04-PITR | Point-in-time recovery | SEC-04 | DEFERRED BY OWNER | launch inicial; ~USD 100/month; reevaluar |
| SEC04-RET | Retención | SEC-04 | PENDING PROD | no inventar |
| SEC04-DRILL | Restore drill | SEC-04 | PRODUCTION-CONDITIONAL | 04C public + 04D storage; Auth físico en PROD |
| SEC04-RPO | RPO | SEC-04 | target ≤24 h daily PROD | dump lógico observado; no PITR |
| SEC04-RTO | RTO | SEC-04 | measured public ~20 min; storage sample ~50 s | target PROD pending |
| SEC04-DB | Recuperación schema/datos public | SEC-04 | FIXED (drill) | 130 tablas; 1061–1065 |
| SEC04-AUTH | Auth hosted | SEC-04 | PHYSICAL PRO (unverified) | COPY dump FAIL; fallback re-enroll |
| SEC04-STOR | Bytes de Storage | SEC-04 | FIXED (tooling+sample) | job diario PROD pendiente |
| SEC04-EDGE | Redeploy desde repo | SEC-04 | FIXED (procedimiento) | código en Git |
| SEC05-P0 | Moderation/search AAL1 | SEC-05 / 1064 | FIXED | P0 cerrado |
| SEC05-ACTOR | revoke/hide/proposal/vet-care actor | SEC-05 / 1064 | FIXED | P0/P1 cerrado |
| SEC05-GRANT | DML residual deny-by-RLS | SEC-05 / 1065 | FIXED | least privilege |
| SEC05-MARKETS | `country_markets` sin RLS (SEC-01) | SEC-01/05 | FIXED | RLS ON + SELECT + write grants revocados |
| SEC05-MIME | `allowed_mime_types` null en buckets | SEC-FINAL | FIXED (4) | 1066 = register allowlist |
| SEC05-MIME-EVIDENCE | `moderation-evidence` MIME null | SEC-FINAL | NOT APPLICABLE TO CURRENT UPLOAD SURFACE / FUTURE DEPLOYMENT REQUIREMENT | sin INSERT cliente; no OPEN RISK |
| SEC05-LEGACY-BUCKET | Android `profile-avatars` / `organization-media` no existen en STAGING | SEC-FINAL | OPEN | INFO / residual código |
| SECF-EDGE-UNDEPLOYED | `push`, `delete-account`, `klipy-proxy` | SEC-03 | NOT IN CURRENT ATTACK SURFACE | review antes de deploy |
| SECF-SECRET | service_role / OAuth secret en Git/Android | SEC-FINAL | FIXED (inspección) | solo nombres; ver secrets |
| SECF-FIREBASE | `app/google-services.json` tracked (client config) | SEC-FINAL | OPEN | INFO; restringir en consola PROD |

Ningún hallazgo se marca ACCEPTED RISK. Cursor no acepta riesgos.

## Revalidación STAGING (SEC-FINAL)

- public tables: 130; `relrowsecurity` off: **0**.
- Admin/staff/moderation RPCs privilegiadas: AAL2 vía `_canon_require_admin_aal2` (lista en `tests/sec_final_admin_rpcs.sql`).
- `get_admin_session`: AAL2 vía `_canon_admin_aal()`.
- `list_admin_staff` / `get_admin_staff` / `list_admin_staff_audit`: AAL2.
- 1065: INSERT/UPDATE/DELETE revocado en 8 tablas residuales; SELECT conservado.
- Buckets live: `public-media` (public, 50 MiB), `private-media` / `documents` / `moderation-evidence` (private, 50 MiB), `vitacora-import` (private, 5 MiB). MIME 1066 en 4 buckets; `moderation-evidence` null = N/A current upload / FUTURE DEPLOYMENT REQUIREMENT.  
- `signed_url.request` 1067: 60 / 600 s, fail-closed.
- `country_markets`: RLS ON, SELECT policy, sin write policy.
