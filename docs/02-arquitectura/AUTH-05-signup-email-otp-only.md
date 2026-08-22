# AUTH-05 — Signup email OTP-only

Canonical Staging. Does not undo AUTH-01/02/03/04.

## Product

`SIGNUP_CONFIRMATION_MODE = EMAIL_OTP_ONLY`

LeoVer signup email verification is the numeric code only. ConfirmationURL is **not** part of the signup UX.

The hosted Confirm signup template is managed in the Supabase Dashboard and is expected to use `{{ .Token }}`, not `{{ .ConfirmationURL }}`. Android must not overwrite hosted email templates.

Password recovery and other Auth flows may still use `com.comunidapp.app://login-callback`. That deep-link infrastructure stays.

## OTP input

Supabase email OTP length is configurable from 6 to 10 digits. Staging currently emits 8.

- numeric only
- minimum 6, maximum 10
- do not pad, invent, or truncate a valid pasted code
- Verify enabled only when length is in 6..10

## After OTP

Valid OTP → session → canonical PERSON load/provision idempotently → username reused → onboarding only if incomplete → Home.

Invalid or expired OTP: friendly error. Pending signup state is kept.

Resend sends a fresh OTP email, shows “Te enviamos un nuevo código.”, and respects rate limits.

## Unverified auth user (Confirm Email ON)

`SIGNUP_ACCEPTED` may already have created `auth.users` with `email_confirmed_at = null`. That is expected.

- Session stays unavailable until OTP succeeds.
- Unverified auth users never get normal LeoVer app access.
- Do not delete the auth user because verification is pending.
- Resend confirmation reuses that unconfirmed account (`resendEmail` SIGNUP). It must not create a duplicate.
