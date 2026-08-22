# QA-01 — Canonical Staging demo universe

Staging-only. **Not** a migration. Never run against legacy or production.

```
EXPECTED_PROJECT_REF = tobqbddfcyitwgbkthhy
LEGACY_PROJECT_REF   = wystsapjfpdtoprlmizz   (ABORT)
```

## Commands

From repo root, with canonical workdir:

```powershell
$env:LEOVER_QA_PASSWORD = "<local only, never commit>"
.\infra\supabase-canonical\qa\qa01.ps1 -Action seed
.\infra\supabase-canonical\qa\qa01.ps1 -Action reset
.\infra\supabase-canonical\qa\qa01.ps1 -Action reseed
```

If `LEOVER_QA_PASSWORD` is absent, the script aborts with `QA_PASSWORD_REQUIRED` and does not invent a password.

Auth users are created via Supabase Admin (`SUPABASE_SERVICE_ROLE_KEY` from the local environment only). Domain rows are upserted by `qa_` username / `qa-` slug / `QA •` display name.

`velu` is never deleted.

## Fixture tags

| Tag | Meaning |
|-----|---------|
| username `qa_*` | QA-01 PERSON |
| email `qa.*@leoverapp.com` | QA-01 auth mailbox (fictional) |
| org slug `qa-*` | QA-01 organization |
| display `QA • ...` | QA-01 public label |

Admin-confirmed QA users are **not** proof of the normal signup/OTP flow.
