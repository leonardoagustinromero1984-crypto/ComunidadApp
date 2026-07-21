#!/usr/bin/env bash
# LeoVer M08 Etapa 4C — quality gate: integración local + preparación smoke APK.
# No remotes. No secrets. No migration 037.
set -euo pipefail
ROOT="${ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
FAIL=0
MIG="$ROOT/supabase/migrations"

echo "== M08 Stage 4C local integration / smoke prep quality =="

require_file() {
  local f="$1"
  if [[ ! -f "$f" ]]; then
    echo "MISSING: $f"
    FAIL=1
    return
  fi
  local sz
  sz=$(wc -c <"$f" | tr -d ' ')
  if [[ "$sz" -lt 200 ]]; then
    echo "TOO_SMALL: $f ($sz bytes)"
    FAIL=1
  else
    echo "OK file: $f ($sz bytes)"
  fi
}

require_file "$ROOT/docs/02-arquitectura/M08-etapa-4c-integracion-local-smoke-apk.md"
require_file "$ROOT/docs/04-calidad/M08-reporte-validacion-integracion-local-4c.md"
require_file "$ROOT/docs/04-calidad/M08-checklist-smoke-apk-local.md"
require_file "$ROOT/scripts/sql/m08_validate_local_035.sql"
require_file "$ROOT/scripts/sql/m08_validate_local_036.sql"
require_file "$ROOT/scripts/sql/m08_prepare_local_smoke_fixtures.sql"
require_file "$ROOT/app/src/main/java/com/comunidapp/app/data/repository/LegacyPetRepositoryAdapter.kt"
require_file "$ROOT/app/src/debug/res/xml/network_security_config.xml"

echo "== Migration max 036 / no 037 =="
highest=$(ls "$MIG" | grep -E '^[0-9]{3}_' | sed 's/_.*//' | sort | tail -n1)
echo "Highest migration: $highest"
if [[ "$highest" != "036" ]]; then
  echo "Expected highest 036, got $highest"
  FAIL=1
fi
if ls "$MIG"/037_* >/dev/null 2>&1; then
  echo "FORBIDDEN: supabase/migrations/037_* exists"
  FAIL=1
fi

echo "== Migrations not modified in working tree =="
if git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  mod=$(git -C "$ROOT" diff --name-only -- supabase/migrations/ || true)
  staged=$(git -C "$ROOT" diff --cached --name-only -- supabase/migrations/ || true)
  if [[ -n "$mod" || -n "$staged" ]]; then
    echo "FORBIDDEN: modifications under supabase/migrations/"
    FAIL=1
  else
    echo "OK migrations clean"
  fi
fi

echo "== local.properties not tracked =="
if git -C "$ROOT" ls-files --error-unmatch local.properties >/dev/null 2>&1; then
  echo "FORBIDDEN: local.properties is tracked"
  FAIL=1
else
  echo "OK local.properties untracked"
fi

echo "== apk/ and *.apk ignored =="
if git -C "$ROOT" check-ignore -q apk/LeoVer-M08-Stage4C-debug.apk 2>/dev/null \
  || git -C "$ROOT" check-ignore -q "*.apk"; then
  echo "OK apk artifacts ignored"
else
  # Fallback: ensure .gitignore has patterns
  if grep -qE '^\*\.apk$|^apk/' "$ROOT/.gitignore"; then
    echo "OK apk ignore patterns present"
  else
    echo "FORBIDDEN: apk ignore missing"
    FAIL=1
  fi
fi

echo "== Release network config must keep cleartext OFF =="
MAIN_NSC="$ROOT/app/src/main/res/xml/network_security_config.xml"
if ! grep -q 'cleartextTrafficPermitted="false"' "$MAIN_NSC"; then
  echo "FORBIDDEN: main network_security_config must disable cleartext"
  FAIL=1
fi
if grep -q '10.0.2.2' "$MAIN_NSC"; then
  echo "FORBIDDEN: localhost/emulator cleartext must not be in main/release config"
  FAIL=1
fi
echo "OK release cleartext OFF"

echo "== Debug cleartext limited to local hosts =="
DBG_NSC="$ROOT/app/src/debug/res/xml/network_security_config.xml"
grep -q '10.0.2.2' "$DBG_NSC" || { echo "MISSING 10.0.2.2 in debug NSC"; FAIL=1; }
grep -q 'cleartextTrafficPermitted="true"' "$DBG_NSC" || { echo "MISSING debug cleartext allow"; FAIL=1; }

echo "== No service_role in Android main/debug sources =="
# Same policy as stage4b: allow deny-list/sanitization catalogs and explanatory comments only.
hits=$(grep -RIn --include='*.kt' --include='*.xml' --include='*.properties' -E 'service_role' \
  "$ROOT/app/src/main" "$ROOT/app/src/debug" 2>/dev/null \
  | grep -vE 'MetadataAllowlist\.kt|Sanitization\.kt' \
  | grep -vE ':[0-9]+:[[:space:]]*//' \
  || true)
if [[ -n "$hits" ]]; then
  echo "$hits"
  echo "FORBIDDEN service_role in Android sources"
  FAIL=1
else
  echo "OK no service_role (deny-lists/comments excluded)"
fi

echo "== Legacy pets mutate path disabled / adapter active =="
DS="$ROOT/app/src/main/java/com/comunidapp/app/data/remote/supabase/SupabaseDataSources.kt"
if grep -E 'from\(SupabaseTables\.PETS\)\.(insert|update|delete)' "$DS" 2>/dev/null; then
  echo "FORBIDDEN: PetSupabaseDataSource still mutates pets via PostgREST"
  FAIL=1
fi
DP="$ROOT/app/src/main/java/com/comunidapp/app/data/provider/DataProvider.kt"
grep -qF 'LegacyPetRepositoryAdapter' "$DP" || { echo "MISSING LegacyPetRepositoryAdapter"; FAIL=1; }

echo "== Fixtures marked LOCAL TEST DATA ONLY / no PII markers =="
FIX="$ROOT/scripts/sql/m08_prepare_local_smoke_fixtures.sql"
grep -qF 'LOCAL TEST DATA ONLY' "$FIX" || { echo "MISSING LOCAL TEST DATA ONLY"; FAIL=1; }
if grep -Eiq 'service_role|eyJhbGci|@[a-z0-9.-]+\.(com|ar)\b|password\s*=' "$FIX"; then
  echo "FORBIDDEN credential/PII-like pattern in fixtures"
  FAIL=1
fi

echo "== Docs state markers =="
for f in \
  "$ROOT/docs/02-arquitectura/M08-etapa-4c-integracion-local-smoke-apk.md" \
  "$ROOT/docs/04-calidad/M08-reporte-validacion-integracion-local-4c.md" \
  "$ROOT/docs/04-calidad/M08-checklist-smoke-apk-local.md"
do
  grep -qF 'M08 ETAPA 4C — INTEGRACIÓN LOCAL AUTOMÁTICA PASS' "$f" \
    || { echo "missing AUTO PASS marker in $f"; FAIL=1; }
  grep -qi 'SMOKE APK MANUAL' "$f" || { echo "missing SMOKE MANUAL in $f"; FAIL=1; }
  grep -qi 'STAGING NO AUTORIZADO' "$f" || { echo "missing STAGING block in $f"; FAIL=1; }
done

# Do NOT scan this script for --linked (avoids false positive). Scan docs+fixtures only.
echo "== No remote/linked/secret patterns in 4C docs+fixtures =="
for f in \
  "$ROOT/docs/02-arquitectura/M08-etapa-4c-integracion-local-smoke-apk.md" \
  "$ROOT/docs/04-calidad/M08-reporte-validacion-integracion-local-4c.md" \
  "$ROOT/docs/04-calidad/M08-checklist-smoke-apk-local.md" \
  "$ROOT/scripts/sql/m08_prepare_local_smoke_fixtures.sql"
do
  if grep -Eiq 'db push|migration repair|supabase\.co|eyJ[A-Za-z0-9_-]{20,}' "$f"; then
    echo "FORBIDDEN remote/secret pattern in $f"
    FAIL=1
  fi
done
echo "OK scanned docs/fixtures"

if [[ "$FAIL" -ne 0 ]]; then
  echo "M08 STAGE 4C QUALITY CHECKS FAILED"
  exit 1
fi
echo "M08 STAGE 4C QUALITY CHECKS PASSED"
exit 0
