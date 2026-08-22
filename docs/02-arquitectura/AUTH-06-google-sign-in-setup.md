# AUTH-06 — Continuar con Google (Android / Staging)

Canonical Staging only: `tobqbddfcyitwgbkthhy`  
Never touch Legacy `wystsapjfpdtoprlmizz`. Do not create Production in this block.

Google is an **additional** auth method. Email + password + signup OTP stay.

| Flag | Value |
|---|---|
| `EMAIL_SIGNUP_CONFIRMATION` | `OTP` |
| `GOOGLE_SIGNUP_CONFIRMATION` | `GOOGLE_OAUTH` |
| `GOOGLE_SIGNUP_REQUIRES_OTP` | `NO` |
| `GOOGLE_SIGNUP_REQUIRES_LEOVER_PASSWORD` | `NO` |
| `GOOGLE_SCOPES` | `openid email profile` |
| `EXTRA_GOOGLE_SCOPES` | `NO` |
| `GOOGLE_PROVIDER_TOKEN_PERSISTED` | `NO` |
| `GOOGLE_PROVIDER_REFRESH_TOKEN_PERSISTED` | `NO` |
| `GOOGLE_AVATAR_AUTO_PUBLIC` | `NO` |
| `AUTH_PROVIDER_BECOMES_ACCOUNT_TYPE` | `NO` |
| `MAP_IMPLEMENTATION_PRESERVED` | `YES` |

## Identity rule

`auth.users` (Supabase) → one LeoVer **PERSON** (`persons.user_id` PK) → pets / VitaCora / capabilities / memberships.

Email, Google, and future Apple are identities on the **same** `auth.users` row when Supabase links them. The app never creates a second PERSON for the same auth UUID.

## Android architecture (existing, reused)

| Piece | Value |
|---|---|
| Authority | Supabase Auth (`supabase.auth`), not Firebase Auth, not GoogleSignIn |
| Client | `SupabaseClientProvider` Auth plugin, `FlowType.PKCE` |
| Redirect | `com.comunidapp.app://login-callback` |
| Manifest | `scheme=com.comunidapp.app` `host=login-callback` |
| Deep link | `MainActivity` `singleTask` → `supabase.handleDeeplinks` |
| Session | Existing `observeAuthState` / `SessionViewModel` |
| Post-auth | `PostAuthResolver` from PERSON completeness, not “came from Google” |

Login and Crear cuenta both call `AuthRepository.signInWithGoogle()` → `supabase.auth.signInWith(Google)`.

## Operator setup (manual — Cursor cannot complete hosted OAuth)

`GOOGLE_AUTH_MANUAL_CONFIGURATION_REQUIRED = YES`

Do **not** commit client secrets. Do **not** put them in `local.properties` unless you already store other secrets there privately.

### 1. Google Cloud / Google Auth Platform

1. Create or reuse a Google Cloud project for LeoVer Staging.
2. Configure **Branding** (app name **LeoVer**, support email, logo if required).
3. **Audience:** Testing while developing. Add test users (Google accounts) or the OAuth consent screen will block everyone else.
4. **Data Access / scopes:** only `openid`, `email`, `profile`. Do not add Contacts, Drive, Gmail, Calendar, or location.
5. Create an **OAuth client ID** of type **Web application** (Supabase uses this for the Google provider):
   - Authorized redirect URI (Staging GoTrue callback):

```
https://tobqbddfcyitwgbkthhy.supabase.co/auth/v1/callback
```

6. Optionally create an **Android** OAuth client (package + SHA-1) if Google Auth Platform asks for it. The Android app still completes the session via the Supabase custom scheme, not a native GoogleSignIn token exchange.

### 2. Supabase Staging (Authentication → Providers → Google)

Project: `tobqbddfcyitwgbkthhy`

Enable Google. Paste:

- Client ID (Web)
- Client secret (Web) — dashboard only, never in git

Recommended:

- Enable **Skip nonce checks** only if Google/Supabase docs for this SDK version require it (default: leave SDK/PKCE defaults).
- Enable **Allow users to sign up** if new Google users should create `auth.users`.
- Enable **automatic identity linking** for **confirmed** emails so an existing LeoVer email user can later “Continuar con Google” with the same verified address.

Redirect URLs allowlist must include:

```
com.comunidapp.app://login-callback
```

Site URL must not be `null`.

### 3. Android callback

Already implemented. Do not invent `leover://auth/callback`.

`applicationId` for `localDebug` is `com.comunidapp.app.local`. The **custom scheme stays** `com.comunidapp.app`.

### 4. Testing-mode restrictions

If the Google consent screen is in **Testing**, only allowlisted test users can complete the round trip. Production verification/branding is out of scope until product asks.

### 5. Custom Domain rehearsal (official apex `leover.com.ar`)

Do **not** invent a domain. Canonical public domain is **leover.com.ar**.

| Environment | Intended Auth host | DNS today |
|---|---|---|
| STAGING | `auth-staging.leover.com.ar` | NXDOMAIN — not active |
| PROD (future) | `auth.leover.com.ar` | NXDOMAIN — not wired |

Until Cloudflare CNAME + Supabase Custom Domain are verified:

- Keep `SUPABASE_STAGING_URL` as the API/database/storage endpoint.
- Leave `SUPABASE_STAGING_AUTH_ACTIVE=false` in `local.properties`.
- Google will still show the technical Supabase host. That is an **external blocker**, not an app bug.

When Custom Domain is live:

1. Supabase Staging → Custom Domains: add `auth-staging.leover.com.ar`.
2. Cloudflare DNS: CNAME `auth-staging` → the target Supabase provides (do not guess).
3. Set:

```
SUPABASE_STAGING_AUTH_URL=https://auth-staging.leover.com.ar
SUPABASE_STAGING_AUTH_ACTIVE=true
```

4. Google Cloud Web client — Authorized redirect URIs:

```
https://auth-staging.leover.com.ar/auth/v1/callback
```

Keep the technical callback during transition (same Web client). Copy it from `SUPABASE_STAGING_URL` + `/auth/v1/callback`. Do not type a project-ref from memory.

5. Google Auth Platform branding (operator, no fake verification):

| Field | Value |
|---|---|
| App name | **LeoVer** |
| Logo | Official asset `app/src/main/res/drawable-nodpi/leover_logo_official.png` |
| Support email | Official LeoVer contact in Google Cloud (do not invent) |

If Google requires brand verification for the production consent screen, that stays **pending** until the operator completes it. The app cannot finish that step.

6. Physical check only after DNS resolves:

Continuar con Google → account chooser → Google shows LeoVer / `auth-staging.leover.com.ar` → callback → LeoVer session → same PERSON.

## Product behaviour after Google

1. Supabase session becomes the app session.
2. No OTP. No LeoVer password prompt.
3. If PERSON exists for `auth.uid()` and onboarding is complete → Home (tutorials unchanged).
4. If PERSON missing or incomplete → legal consent (if required) then profile onboarding (username, birth date, provincia/localidad). Display name may be prefilled from Google `name` / `full_name`. Google photo is **not** copied as public avatar.
5. Logout signs out Supabase only.

## Unconfirmed email + Google (same address)

Supabase does **not** auto-link unconfirmed identities.

`handle_new_user` (1032) does **not** create PERSON while `email_confirmed_at` is null. PERSON is created when OTP confirms (UPDATE trigger) or via `canon_provision_my_person` after Google onboarding.

Expected:

- Pending OTP email user: `auth.users` exists, no LeoVer app access (`SignupSessionPolicy`), **no PERSON**.
- Completing OTP later creates PERSON for that auth UUID.
- Google with the same address is usually blocked by unique email on `auth.users` until the pending row is confirmed or removed (hosted-dependent).
- Automatic linking of **confirmed** emails is a Staging dashboard setting, not app-created users.

`CODE_TEST` covers the PERSON delay. `REAL_HOSTED_AUTH_TEST` is required for identity linking.

## Database

Migration **1032** (Staging): relax `handle_new_user`; add `canon_provision_my_person` (`auth.uid()` only).

## Physical QA (after hosted config)

A. New Google → onboarding → Home  
B. Logout → Google → Home, no repeated onboarding/tutorials  
C. Confirmed email LeoVer user → logout → Google same email → same PERSON  
D. Cancel Google → stay on auth  
E–H. Email login, signup OTP, recovery, process death session restore  

`CODE_TEST` can pass without a live Google provider. `REAL_HOSTED_AUTH_TEST` cannot.
