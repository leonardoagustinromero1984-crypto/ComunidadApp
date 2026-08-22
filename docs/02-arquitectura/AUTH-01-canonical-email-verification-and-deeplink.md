# AUTH-01 — Canonical email verification and deep link

Canonical Staging only: `tobqbddfcyitwgbkthhy`  
Never touch legacy `wystsapjfpdtoprlmizz` or production Auth.

## Root cause

Physical QA: signup confirmation email arrived **without a visible 6-digit code**, and tapping the confirmation link redirected to a **null / anull** destination.

| Symptom | Cause |
| --- | --- |
| No visible OTP | Hosted Staging **Confirm signup** template is still the default Supabase English template. It uses only a link. It does **not** include `{{ .Token }}`. Repo templates in `supabase/email-templates/` were never applied to this project. |
| Link → `null` / `anull` | GoTrue builds `{{ .ConfirmationURL }}` as verify-endpoint + `redirect_to=<Site URL or signup redirectUrl>`. If the Android callback is **not allowlisted**, GoTrue drops it and falls back to Site URL. An empty/null Site URL (or Java/JS `"a" + null` / `"$nullable"` interpolation) becomes the literal destination `null` / `anull`. The app already sent `com.comunidapp.app://login-callback`; the hosted allowlist/Site URL did not match. |

Do not disable Confirm Email. Do not treat this as a database bug.

## Previous broken behavior

1. User signs up in LeoVer.
2. Email arrives without a code.
3. User taps the only action (the link).
4. Browser follows Supabase verify and then redirects to `null` / `anull`.
5. App never receives a valid callback; account cannot be confirmed reliably.

## Canonical Android callback (already existed — kept)

Do **not** invent `leover://auth/callback`. The repo already had one stable callback:

| Key | Value |
| --- | --- |
| `CANONICAL_ANDROID_AUTH_SCHEME` | `com.comunidapp.app` |
| `CANONICAL_ANDROID_AUTH_HOST` | `login-callback` |
| `CANONICAL_ANDROID_AUTH_REDIRECT` | `com.comunidapp.app://login-callback` |

Must agree between:

- `AndroidManifest.xml` intent-filter
- `install(Auth) { scheme / host }`
- `signUpWith(..., redirectUrl = …)` and `resetPasswordForEmail(..., redirectUrl = …)`
- Staging Dashboard **Redirect URLs** allowlist

`applicationId` for `localDebug` is `com.comunidapp.app.local`. The **custom scheme is independent** of `applicationId` and stays `com.comunidapp.app`.

## Canonical auth flow

```
SIGNUP (email + password + username + birth date)
  → auth.users insert
  → handle_new_user() creates PERSON (one row, PK user_id)
  → confirmation email
  → screen "Verificá tu correo"
  → user enters 6-digit {{ .Token }}
  → verifyEmailOtp(SIGNUP)
  → authenticated session kept
  → app continues (consent / profile / Home)
```

Secondary path: tap **Confirmar correo en LeoVer** (`{{ .ConfirmationURL }}`) → Supabase verify → `com.comunidapp.app://login-callback` → `supabase.handleDeeplinks` (cold start and `onNewIntent`).

## OTP flow

- 6 numeric digits, paste supported, spaces stripped, never logged, never persisted, never sent to analytics.
- Real API: `supabase.auth.verifyEmailOtp(type = OtpType.Email.SIGNUP, email, token)`.
- After success the session is **kept** (no sign-out).
- Resend: `supabase.auth.resendEmail(OtpType.Email.SIGNUP, email)`. Not a fake second `signUpWith`.
- Rate limit UI: `Esperá un momento antes de solicitar otro código.`

## Deep link handling

- `MainActivity` `singleTask`: `onCreate` + `onNewIntent` → `handleDeeplinks`.
- Forbidden destinations (`null`, `anull`, `://null`) never forwarded to the SDK.
- Error query (`otp_expired`, `access_denied`, already used) → `El enlace venció o ya fue utilizado.` + Reenviar código.
- Recovery `type=recovery` → `PasswordResetActive` screen.

## PERSON provisioning

Intentional: PERSON is created on `auth.users` insert (before email confirmation) by `handle_new_user()`. Retries are idempotent (`user_id` PK). One authenticated human → one PERSON. No migration 1025.

## Email change

Not implemented in the active Android settings UI. **N/A** — do not build a new feature. Template files exist for a future dashboard paste only.

## Web callback

Route: `/acceso/callback`.  
`getLoginRedirectPath` rejects `null` / `anull` / `undefined` / protocol-relative URLs.  
Staging Android must **not** use production `https://leover.com.ar` as its Auth redirect.

## Auth URL matrix (planning)

| ENVIRONMENT | PLATFORM | SITE_URL | REDIRECT_URL | CALLBACK_HANDLER |
| --- | --- | --- | --- | --- |
| STAGING | Android | `com.comunidapp.app://login-callback` | `com.comunidapp.app://login-callback` | `MainActivity` + `handleDeeplinks` |
| STAGING | Web (local/dev) | `http://127.0.0.1:3000` or staging web origin | same origin `/acceso/callback` | `web/app/(auth)/acceso/callback/route.ts` |
| Production (future, do not change remotely) | Android | production callback (same scheme unless later productized) | same | `MainActivity` |
| Production (future, do not change remotely) | Web | `https://leover.com.ar` | `https://leover.com.ar/acceso/callback` | Next.js callback |

Production rows are documentation only. This change does not modify production Auth.

## Security

- Client uses publishable/anon key only.
- No service_role, SMTP password, OTP, access token, or refresh token in logs, docs, committed config, or APK resources.
- OTP is not logged.

## SUPABASE_DASHBOARD_MANUAL_ACTION_REQUIRED = YES

Cursor has no safe Management API session here to mutate hosted Auth templates. **Do not hack `auth` tables.**

Project: **tobqbddfcyitwgbkthhy only**  
Dashboard: [Authentication](https://supabase.com/dashboard/project/tobqbddfcyitwgbkthhy/auth/templates)

### 1. URL Configuration

Path: Authentication → URL Configuration

- **Site URL:** `com.comunidapp.app://login-callback`
- **Redirect URLs** (add exact values, one per line):
  - `com.comunidapp.app://login-callback`
  - `http://127.0.0.1:3000/**` (optional local web)
  - `http://localhost:3000/**` (optional local web)

Do **not** add production `https://leover.com.ar` as the Staging Android callback.

Keep:

- Allow new users to sign up = ON
- Confirm email = ON
- Email provider enabled = ON

### 2. Confirm signup template

Path: Authentication → Email Templates → **Confirm signup**

**Subject:**

```
Confirmá tu correo en LeoVer
```

**Body (HTML):** paste `supabase/email-templates/confirm-signup-body.html` (must include `{{ .Token }}` and `{{ .ConfirmationURL }}`).

### 3. Reset password template

Path: Authentication → Email Templates → **Reset password**

**Subject:**

```
Restablecé tu contraseña de LeoVer
```

**Body (HTML):** paste `supabase/email-templates/reset-password-body.html` (must include `{{ .ConfirmationURL }}`).

Custom SMTP is **not** required for this Staging code fix. Default provider rate limits are `EMAIL_PROVIDER_RATE_LIMIT`, not an app failure.

## Test status

- Focused Android auth tests (redirect, OTP, deep link, resend, recovery navigation)
- `compileLocalDebugKotlin`
- Web `auth-redirect` test
- Inbox click-through: `EMAIL_INBOX_MANUAL_QA_REQUIRED = YES`

No emulator. No ADB. No commit. No push.

## AUTH-02 follow-up (onboarding / geography)

AUTH-01 does **not** cover profile onboarding or the Province catalog. After AUTH-01, physical QA still found: username asked twice against an existing PERSON, empty Province selector, and an auth user that can exist when the confirmation redirect visually failed. That work is `AUTH-02-onboarding-profile-geography-fix.md`. Do not undo this AUTH-01 redirect/OTP hardening when applying AUTH-02. Hosted Dashboard paste remains pending here.
