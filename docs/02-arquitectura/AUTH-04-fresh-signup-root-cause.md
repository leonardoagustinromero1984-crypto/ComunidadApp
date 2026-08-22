# AUTH-04 — Fresh signup root cause

Canonical Staging only. Does not rewrite AUTH-01/02/03 reports.

## Physical failure

Visible copy: “No pudimos completar el inicio de sesión. Intentá de nuevo.” (`UNKNOWN_AUTH_ERROR`).

Staging inspect (read-only, no emails): **1** `auth.users` (the existing confirmed QA PERSON). **0** unconfirmed users. No new auth row from the failed physical attempt.

`AUTH_USER_WAS_CREATED = NO`  
`AUTH_LOG_ACCESS = MANUAL_DASHBOARD_REQUIRED` (CLI has no Auth log reader; SMTP settings are hosted-only).

## Android defect (definite)

`SupabaseAuthRepository.register` treated `signUpWith == null && currentUser == null` as `UNKNOWN_AUTH_ERROR`. With Confirm Email ON, a missing session after an accepted signup is **not** a login failure. That path now returns success and the app opens Verify Email.

This defect would fire if GoTrue accepted signup without returning a user/session payload. It does **not** by itself explain a missing `auth.users` row.

## What the missing auth row means

The physical attempt never persisted `auth.users`. The Android session-mapping defect above is real and was correctly fixed, but it is **not** the hosted-mail failure.

## Physical server root cause (manual)

`AUTH_04_SERVER_ROOT_CAUSE = RESEND_UNVERIFIED_SENDER_DOMAIN`

Resend rejected the signup confirmation email:

```
550 The leover.com.ar domain is not verified.
```

Temporary/current Staging sender was corrected to a sender under the verified domain `leoverapp.com`.

Do not store SMTP password or API key in this repo. Hosted Auth email settings stay in the Supabase Dashboard.

The Android immediate-session bug found by AUTH-04 remains a separate real defect that was correctly fixed.

## Contract now

Signup accepted + no session → verification screen.  
PERSON is created by `handle_new_user` on auth insert (may precede confirmation). App does not call RLS-protected PERSON writes until a confirmed session exists.  
Signup confirmation mode is documented in AUTH-05: `SIGNUP_CONFIRMATION_MODE = EMAIL_OTP_ONLY`.
