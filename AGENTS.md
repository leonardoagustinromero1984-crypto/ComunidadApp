# LeoVer agent contract

This file is the Cloud/handoff contract. Do not duplicate the brand or Fast-regression details already in `.cursor/rules/`.

## Git and environments

- Never commit or push directly to `main`.
- Never merge to `main` or auto-merge PRs unless a human explicitly asks.
- Never target PROD. STAGING only: `tobqbddfcyitwgbkthhy`.
- Do not `git reset`, `git restore`, `git clean`, or broad revert unless the user explicitly authorizes that exact command.
- Do not modify already-applied migrations. Add a new migration if the database must change.
- Never commit secrets: `.env`, `local.properties`, keystores, service_role values, QA passwords, `scripts/qa/.qa-*.local.json`.

## Tests (Definition of Done)

Daily default is Fast / Affected JVM tests. See `.cursor/rules/leover-regression-done.mdc`.

- Run `scripts/qa/run-regression.ps1 -Affected` (or the Linux equivalent documented in `docs/cloud/CURSOR-CLOUD-SETUP.md`).
- Do not start an Android emulator or Maestro unless the user explicitly asks (`-Maestro`, `-SmokeE2E`, `-FullE2E`).
- Physical / hardware QA is never PASS from Cloud. Mark `MANUAL_REQUIRED` / `NOT RUN`.

## Cloud

- Work only on a `cloud-handoff/*` or feature branch, never on `main`.
- LeoVer-QA emulator is paused for local PCs and is not the Cloud default.
- A Cloud agent must not declare Android E2E or physical QA PASS.
