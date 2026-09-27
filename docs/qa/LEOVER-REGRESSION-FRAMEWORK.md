# LeoVer regression framework

Permanent rule: a feature, behavior change, or bugfix is not done without tests that exist, were updated, and were run.

Daily work uses the **cheap** layer. LeoVer-QA and Maestro stay available but **off** unless asked.

## Commands

```powershell
# Default = Fast. No emulator. No Maestro.
.\scripts\qa\run-regression.ps1
.\scripts\qa\run-regression.ps1 -Fast

# git diff (read-only) → modules → cheap tests only
.\scripts\qa\run-regression.ps1 -Affected

# Coverage gate only (no Gradle if you just want the map)
.\scripts\qa\check-regression-coverage.ps1

# Explicit Android E2E (starts LeoVer-QA only then; stops after unless -KeepEmulator)
.\scripts\qa\run-regression.ps1 -SmokeE2E
.\scripts\qa\run-regression.ps1 -Maestro
.\scripts\qa\run-regression.ps1 -FullE2E
```

`-ResetAndSeed` is opt-in and only meaningful with Maestro flags (STAGING wipe). Do not use it for Fast.

## Files

| File | Role |
| --- | --- |
| `docs/qa/regression-catalog.yaml` | Rule IDs, tests, status |
| `docs/qa/change-impact-map.yaml` | Path → module → Gradle filters |
| `docs/qa/LEOVER-REGRESSION-MATRIX.md` | Human matrix + gaps |
| `.cursor/rules/leover-regression-done.mdc` | Agent Definition of Done |
| `scripts/qa/run-regression.ps1` | Fast / Affected / on-demand E2E |
| `scripts/qa/check-regression-coverage.ps1` | Fail-closed coverage vs diff |

## Order (when a heavy run is requested)

1. Coverage gate
2. Unit / contract / ViewModel
3. Backend SQL probes (only if `-Backend` and scripts exist)
4. Maestro smoke
5. Maestro impacted
6. Full E2E

Stop dependents if a cheaper critical layer fails.

## CI later

`run-regression.ps1 -FullE2E` is the hook for cloud/CI. Do not enable expensive GitHub Actions in this block.

## Emulator

AVD `LeoVer-QA` remains. Default is **not** started. After an explicit Maestro run the runner stops it unless `-KeepEmulator`.
