# Android CI — migration guard without fixed ceiling

## Cause of Android CI failure on `f7ce081`

Step `M07 local quality checks` failed before assemble/test/lint:

```text
Highest migration: 081
Expected highest migration 052, got 081
QUALITY CHECKS FAILED
```

`scripts/ci/m07_quality_checks.sh` still hardcoded ceiling **052** (M14-era baseline).
Repo migraciones legítimas: **001–081** contiguous.

## Fix (IOS-PILOT-1.1)

Option A: remove historical fixed ceiling; keep security controls:

- duplicate detection
- contiguous numbering from 001 (no gaps)
- format `NNN_`
- report derived highest

Does **not** touch SQL / apply / repair / staging / prod.

## Controls still active

- Kotlin↔SQL event/metric/health catalogs
- M07 permissions
- SECURITY DEFINER / search_path
- secret pattern scan
- SQL basic lint
- prior migrations 001–019 edit guard
