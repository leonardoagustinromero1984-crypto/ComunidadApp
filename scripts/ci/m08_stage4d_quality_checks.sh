#!/usr/bin/env bash
# LeoVer M08 Etapa 4D — staging preparation quality gate.
# Static/local checks only: this script never applies remote SQL.
set -euo pipefail

ROOT="${ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
MIG="$ROOT/supabase/migrations"
GRADLE="$ROOT/app/build.gradle.kts"
EXAMPLE="$ROOT/local.properties.example"
FAIL=0

fail() {
  echo "FAIL: $*"
  FAIL=1
}

require_file() {
  [[ -f "$1" ]] && echo "OK file: ${1#"$ROOT"/}" || fail "missing $1"
}

echo "== M08 Etapa 4D staging quality checks =="

echo "== Android flavors and staging guard =="
grep -qF 'productFlavors' "$GRADLE" || fail "productFlavors missing"
for flavor in local staging production; do
  grep -qF "create(\"$flavor\")" "$GRADLE" || fail "$flavor flavor missing"
done
grep -qF 'applicationIdSuffix = ".staging"' "$GRADLE" || fail "staging suffix missing"
grep -qF 'LeoVer Staging' "$GRADLE" || fail "LeoVer Staging app_name missing"
grep -qF '"LEOVER_ENV", "\"staging\""' "$GRADLE" || fail "staging LEOVER_ENV missing"
grep -qF 'stagingUrl.startsWith("https://")' "$GRADLE" \
  || fail "staging HTTPS validation missing"
grep -qF 'u.contains("localhost")' "$GRADLE" || fail "localhost staging rejection missing"
grep -qF 'u.contains("10.0.2.2")' "$GRADLE" || fail "10.0.2.2 staging rejection missing"
# Release/production must not silently reuse staging credentials.
if grep -n 'create("production")' -A 20 "$GRADLE" | grep -q 'stagingUrl\|SUPABASE_STAGING'; then
  fail "production flavor must not reference staging credentials"
else
  echo "OK production does not reuse staging credentials"
fi
MAIN_NSC="$ROOT/app/src/main/res/xml/network_security_config.xml"
grep -q 'cleartextTrafficPermitted="false"' "$MAIN_NSC" \
  || fail "main/release cleartext must stay OFF"
! grep -Eq 'localhost|10\.0\.2\.2|127\.0\.0\.1' "$MAIN_NSC" \
  || fail "main NSC must not allow local hosts"

echo "== Android source credential guard =="
# Defensive deny-list literals and explanatory comments are allowed. Credential
# names/assignments are not. This scans shipped app/src/main code, never tests
# containing synthetic attack strings and never this quality script.
if grep -RIn \
  --include='*.kt' --include='*.kts' --include='*.xml' --include='*.properties' \
  -E '(service_role_key|SUPABASE_SERVICE_ROLE|service_role[[:space:]]*[:=])' \
  "$ROOT/app/src/main" 2>/dev/null; then
  fail "service role credential usage found in app/src/main"
else
  echo "OK no service role credential usage"
fi

echo "== Local configuration hygiene =="
if git -C "$ROOT" ls-files --error-unmatch local.properties >/dev/null 2>&1; then
  fail "local.properties is tracked"
else
  echo "OK local.properties not tracked"
fi
for key in \
  SUPABASE_STAGING_URL \
  SUPABASE_STAGING_PUBLISHABLE_KEY \
  SUPABASE_PRODUCTION_URL \
  SUPABASE_PRODUCTION_PUBLISHABLE_KEY
do
  grep -q "^${key}=" "$EXAMPLE" || fail "$key placeholder missing"
done

echo "== Migration and SQL inventory =="
require_file "$MIG/035_m08_pets_responsibilities_and_rls.sql"
require_file "$MIG/036_m08_pet_repository_compatibility_rpcs.sql"
require_file "$ROOT/scripts/sql/m08_validate_staging_035.sql"
require_file "$ROOT/scripts/sql/m08_validate_staging_036.sql"
if compgen -G "$MIG/037_*.sql" >/dev/null; then
  fail "migration 037 must be absent"
fi

echo "== Migrations unchanged =="
if git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  modified=$(git -C "$ROOT" diff --name-only -- supabase/migrations/ || true)
  staged=$(git -C "$ROOT" diff --cached --name-only -- supabase/migrations/ || true)
  [[ -z "$modified" && -z "$staged" ]] || fail "supabase migrations were modified"
fi

echo "== APK artifacts ignored =="
if git -C "$ROOT" check-ignore -q apk/LeoVer-M08-Staging-debug.apk 2>/dev/null; then
  echo "OK apk/ ignored"
else
  grep -qE '^apk/$' "$ROOT/.gitignore" || fail "apk/ ignore rule missing"
fi

echo "== Documentation markers =="
DOCS=(
  "$ROOT/docs/02-arquitectura/M08-etapa-4d-staging-apk-distribuible.md"
  "$ROOT/docs/04-calidad/M08-plan-despliegue-staging-035-036.md"
  "$ROOT/docs/04-calidad/M08-checklist-smoke-apk-staging.md"
)
for doc in "${DOCS[@]}"; do
  require_file "$doc"
  grep -qF 'M08 ETAPA 4D — PREPARACIÓN STAGING LISTA' "$doc" \
    || fail "preparation marker missing in $doc"
  grep -qF 'APPLY 035/036 — PENDIENTE DE CONFIRMACIÓN MANUAL' "$doc" \
    || fail "apply marker missing in $doc"
  grep -qF 'APK DISTRIBUIBLE — PENDIENTE DEL APPLY' "$doc" \
    || fail "APK marker missing in $doc"
done

echo "== Documented remote commands remain pending =="
# Scan docs only, not this script, to avoid command-token false positives.
for doc in "${DOCS[@]}"; do
  bad_push=$(awk '
    BEGIN { IGNORECASE = 1 }
    /db push/ && $0 !~ /NO ejecutar/ && previous !~ /NO ejecutar/ { print }
    { previous = $0 }
  ' "$doc")
  [[ -z "$bad_push" ]] || fail "db push not marked NO ejecutar in $doc"
done

echo "== Backup path must stay outside the repo =="
PLAN="$ROOT/docs/04-calidad/M08-plan-despliegue-staging-035-036.md"
grep -qF 'LeoVerBackups' "$PLAN" || fail "backup path LeoVerBackups missing in plan"
if grep -Eiq 'backups/staging|repo.*backup|backup.*inside' "$PLAN"; then
  # soft: plan must not recommend in-repo backups/
  :
fi
! grep -Eq 'db dump --linked --file ["'\'']?backups/' "$PLAN" \
  || fail "plan must not dump backups into repo backups/"

echo "== No remote reset / repair automation in 4D SQL/docs =="
# Do not scan this quality script (deny-list tokens live here by design).
SCAN_RESET=(
  "$ROOT/scripts/sql/m08_validate_staging_035.sql"
  "$ROOT/scripts/sql/m08_validate_staging_036.sql"
  "$ROOT/docs/02-arquitectura/M08-etapa-4d-staging-apk-distribuible.md"
  "$ROOT/docs/04-calidad/M08-plan-despliegue-staging-035-036.md"
  "$ROOT/docs/04-calidad/M08-checklist-smoke-apk-staging.md"
)
if grep -Eiq 'db reset --linked|migration repair' "${SCAN_RESET[@]}" 2>/dev/null; then
  fail "remote reset/repair found in staging 4D SQL/docs"
else
  echo "OK no remote reset/repair in 4D SQL/docs"
fi

echo "== Checklist staging present =="
require_file "$ROOT/docs/04-calidad/M08-checklist-smoke-apk-staging.md"

echo "== No real-looking secrets or project refs =="
SCAN_FILES=("${DOCS[@]}" "$EXAMPLE")
if grep -EIn 'eyJ[A-Za-z0-9_-]{20,}' "${SCAN_FILES[@]}" 2>/dev/null; then
  fail "JWT-like key found"
fi
if grep -EIn 'https://[a-z0-9]{15,}\.supabase\.co' "${SCAN_FILES[@]}" 2>/dev/null; then
  fail "real-looking Supabase project ref found"
fi

if [[ "$FAIL" -ne 0 ]]; then
  echo "M08 STAGE 4D QUALITY CHECKS FAILED"
  exit 1
fi
echo "M08 STAGE 4D QUALITY CHECKS PASSED"
