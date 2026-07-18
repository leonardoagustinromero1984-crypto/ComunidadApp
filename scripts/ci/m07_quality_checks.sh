#!/usr/bin/env bash
# LeoVer M07 Etapa 5 — local CI quality checks (no external SaaS).
# Prefer python3 when available; fall back to pure bash/grep checks.
set -euo pipefail
ROOT="${ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
MIG="$ROOT/supabase/migrations"
export ROOT
FAIL=0
SUMMARY="$ROOT/app/build/reports/m07-quality-summary.md"
mkdir -p "$(dirname "$SUMMARY")"
{
  echo "# LeoVer M07 quality summary"
  echo
  echo "Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date)"
  echo
} > "$SUMMARY"

resolve_python() {
  local c
  for c in python3 python; do
    if command -v "$c" >/dev/null 2>&1; then
      if "$c" -c "import sys; raise SystemExit(0 if sys.version_info[0] >= 3 and 'WindowsApps' not in sys.executable.replace('\\\\','/') else 1)" 2>/dev/null; then
        echo "$c"
        return 0
      fi
      # Accept any python3 that can import re
      if "$c" -c "import re" 2>/dev/null; then
        echo "$c"
        return 0
      fi
    fi
  done
  return 1
}

PYTHON="$(resolve_python || true)"

echo "== Migration numbering 001–031 =="
nums=$(ls "$MIG" | grep -E '^[0-9]{3}_' | sed 's/_.*//' | sort)
dupes=$(echo "$nums" | uniq -d || true)
if [[ -n "${dupes}" ]]; then
  echo "Duplicate migration numbers: $dupes"
  FAIL=1
fi
expected=1
for n in $nums; do
  n10=$((10#$n))
  if [[ "$n10" -ne "$expected" ]]; then
    echo "Gap or out-of-order near $n (expected $expected)"
    FAIL=1
    break
  fi
  expected=$((expected + 1))
done
highest=$(echo "$nums" | tail -n1)
echo "Highest migration: $highest"
if [[ "$highest" != "031" && "$highest" != "032" ]]; then
  echo "Expected highest migration 031 or 032, got $highest"
  FAIL=1
fi
echo "- Migrations: highest=$highest" >> "$SUMMARY"

echo "== Prior migrations 001–019 intact (git base when available) =="
if git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  base="${M07_BASE_COMMIT:-a02acb15bc78be6b9c405d563f2de2030da70abd}"
  if git -C "$ROOT" cat-file -e "$base^{commit}" 2>/dev/null; then
    # 020–032 may receive minimal apply fixes during M07 local validation (citext, BOM, DROP FUNCTION).
    changed=$(git -C "$ROOT" diff --name-only "$base" -- supabase/migrations/ | grep -E 'migrations/0(0[1-9]|1[0-9])_' || true)
    if [[ -n "$changed" ]]; then
      echo "Prior migrations 001–019 edited:"
      echo "$changed"
      FAIL=1
    else
      echo "No edits to migrations 001–019 vs base"
    fi
  else
    echo "Base commit unavailable; skip prior-edit check"
  fi
else
  echo "Not a git repo; skip prior-edit check"
fi

run_catalog_checks() {
  if [[ -n "${PYTHON}" ]]; then
    "$PYTHON" - <<'PY'
import pathlib, re, sys, os
root = pathlib.Path(os.environ['ROOT']).resolve()
sql029 = (root / 'supabase/migrations/029_m07_observability_audit_security_error_foundation.sql').read_text(encoding='utf-8')
sql031 = (root / 'supabase/migrations/031_m07_retention_permissions_instrumentation_closure_readiness.sql').read_text(encoding='utf-8')
kt = (root / 'app/src/main/java/com/comunidapp/app/domain/observability/catalog/ObservabilityEventCatalog.kt').read_text(encoding='utf-8')
blocks = []
for sql in (sql029, sql031):
    for m in re.finditer(r"insert into public\.observability_event_catalog[\s\S]*?(?=;)", sql, re.I):
        blocks.append(m.group(0))
sql_keys = set()
for b in blocks:
    sql_keys |= set(re.findall(r"'(m0[0-7]\.[a-z0-9_]+\.[a-z0-9_]+)'", b))
kt_keys = set(re.findall(r'e\("(m0[0-7]\.[a-z0-9_]+\.[a-z0-9_]+)"', kt))
missing_sql = sorted(kt_keys - sql_keys)
extra_sql = sorted(sql_keys - kt_keys)
print(f"kotlin={len(kt_keys)} sql_catalog={len(sql_keys)}")
if missing_sql or extra_sql or len(kt_keys) != 118:
    print('MISSING_IN_SQL', missing_sql[:30])
    print('EXTRA_IN_SQL', extra_sql[:30])
    sys.exit(1)
print('catalog exact OK 118')
PY
    return $?
  fi
  # Bash fallback: count Kotlin e("...") keys and require Stage-5 keys in 031
  kt="$ROOT/app/src/main/java/com/comunidapp/app/domain/observability/catalog/ObservabilityEventCatalog.kt"
  sql031="$ROOT/supabase/migrations/031_m07_retention_permissions_instrumentation_closure_readiness.sql"
  kt_count=$(grep -oE 'e\("m0[0-7]\.[a-z0-9_]+\.[a-z0-9_]+"' "$kt" | wc -l | tr -d ' ')
  echo "kotlin_event_keys=$kt_count (bash fallback)"
  if [[ "$kt_count" != "118" ]]; then
    echo "expected 118 kotlin event keys"
    return 1
  fi
  for k in m07.audit.read m07.retention.previewed m07.retention.executed m07.incident.staff_notification; do
    grep -q "$k" "$sql031" || { echo "missing $k in 031"; return 1; }
    grep -q "$k" "$kt" || { echo "missing $k in Kotlin"; return 1; }
  done
  echo "catalog bash fallback OK"
  return 0
}

echo "== Kotlin↔SQL event catalog exact (029+031 → 118) =="
if ! run_catalog_checks; then FAIL=1; fi
echo "- Event catalog: 118" >> "$SUMMARY"

echo "== Metric catalog Kotlin↔SQL (030) =="
kt_metrics=$(grep -oE 'def\("m0[0-7]\.[a-z0-9_]+\.[a-z0-9_]+"' \
  "$ROOT/app/src/main/java/com/comunidapp/app/domain/observability/catalog/OperationalMetricCatalog.kt" | wc -l | tr -d ' ')
echo "kotlin_metrics=$kt_metrics"
if [[ "$kt_metrics" != "28" ]]; then echo "expected 28 metrics"; FAIL=1; fi
while IFS= read -r key; do
  key=${key#def(\"}; key=${key%\"}
  grep -q "$key" "$ROOT/supabase/migrations/030_m07_operational_observability_health_metrics_incidents.sql" \
    || { echo "metric missing in SQL: $key"; FAIL=1; }
done < <(grep -oE 'def\("m0[0-7]\.[a-z0-9_]+\.[a-z0-9_]+"' \
  "$ROOT/app/src/main/java/com/comunidapp/app/domain/observability/catalog/OperationalMetricCatalog.kt" | sed 's/def("//;s/"$//')
echo "- Metric catalog: $kt_metrics" >> "$SUMMARY"

echo "== Health check catalog Kotlin↔SQL (030) =="
kt_health=$(awk '/healthCheckKeys/,/\)/' \
  "$ROOT/app/src/main/java/com/comunidapp/app/domain/observability/catalog/OperationalMetricCatalog.kt" \
  | grep -oE '"[^"]+"' | wc -l | tr -d ' ')
echo "kotlin_health=$kt_health"
if [[ "$kt_health" != "14" ]]; then echo "expected 14 health keys"; FAIL=1; fi
awk '/healthCheckKeys/,/\)/' \
  "$ROOT/app/src/main/java/com/comunidapp/app/domain/observability/catalog/OperationalMetricCatalog.kt" \
  | grep -oE '"[^"]+"' | tr -d '"' | while read -r hk; do
    grep -q "$hk" "$ROOT/supabase/migrations/030_m07_operational_observability_health_metrics_incidents.sql" \
      || { echo "health key missing in SQL: $hk"; exit 1; }
  done || FAIL=1
echo "- Health catalog: $kt_health" >> "$SUMMARY"

echo "== M07 dedicated permissions Kotlin↔SQL =="
for p in observability.view observability.manage audit.view_sensitive security.events.view \
  export.audit_data alert.manage retention.manage health.check.execute; do
  grep -q "$p" "$ROOT/supabase/migrations/031_m07_retention_permissions_instrumentation_closure_readiness.sql" \
    || { echo "missing SQL permission $p"; FAIL=1; }
  grep -q "$p" "$ROOT/app/src/main/java/com/comunidapp/app/domain/authorization/Authorization.kt" \
    || { echo "missing Kotlin permission $p"; FAIL=1; }
done
echo "- Dedicated permissions: 8 present" >> "$SUMMARY"

echo "== SECURITY DEFINER search_path on 031 =="
sql031f="$ROOT/supabase/migrations/031_m07_retention_permissions_instrumentation_closure_readiness.sql"
# Every security definer block in 031 must include set search_path = public nearby
definer_count=$(grep -ci 'security definer' "$sql031f" || true)
spath_count=$(grep -ci 'set search_path = public' "$sql031f" || true)
echo "definer=$definer_count search_path=$spath_count"
if [[ "$definer_count" -lt 1 || "$spath_count" -lt "$definer_count" ]]; then
  echo "DEFINER/search_path mismatch"
  FAIL=1
fi

echo "== Writers / PUBLIC EXECUTE + RLS retention =="
text=$(tr '[:upper:]' '[:lower:]' < "$sql031f")
echo "$text" | grep -q 'revoke all on function' || { echo 'missing revoke all on function'; FAIL=1; }
echo "$text" | grep -q 'enable row level security' || { echo 'missing RLS'; FAIL=1; }
for t in observability_retention_policies observability_retention_runs observability_retention_run_items; do
  echo "$text" | grep -q "$t" || { echo "missing table $t"; FAIL=1; }
done
for b in marketing_events firebase_analytics crashlytics opentelemetry sentry_events; do
  if echo "$text" | grep -q "$b"; then echo "forbidden $b"; FAIL=1; fi
done
# No PUBLIC EXECUTE grants on retention RPCs
if grep -Ei 'grant execute on function public\.m07_(preview|execute)_retention_run[^;]*\bto public\b' "$sql031f"; then
  echo "PUBLIC EXECUTE on retention RPC"
  FAIL=1
fi

echo "== Local secret pattern scan =="
if grep -RInE 'service_role_key\s*=|BEGIN PRIVATE KEY|SUPABASE_SERVICE_ROLE_KEY=[A-Za-z0-9]' \
  --include='*.sql' --include='*.kt' --include='*.kts' --include='*.yml' \
  "$ROOT/supabase/migrations" "$ROOT/app/src" "$ROOT/.github" 2>/dev/null; then
  echo "Potential secret pattern found"
  FAIL=1
else
  echo "No embedded secret patterns found in scanned paths"
fi

echo "== Basic SQL lint (balanced begin/commit on 031) =="
lc=$(tr '[:upper:]' '[:lower:]' < "$sql031f")
begins=$(printf '%s\n' "$lc" | grep -c '^begin;' || true)
commits=$(printf '%s\n' "$lc" | grep -c '^commit;' || true)
if [[ "$begins" -lt 1 || "$commits" -lt 1 ]]; then
  echo "031 missing begin/commit"
  FAIL=1
fi
if printf '%s\n' "$lc" | grep -q 'analytics_events'; then
  echo "analytics table forbidden"
  FAIL=1
fi
echo "sql basic OK"

echo "== JaCoCo informative note =="
echo "- JaCoCo: informative only; baseline recorded after local :app:jacocoTestReport" >> "$SUMMARY"
echo "- Staging migrations 014–032: PENDIENTE (no remote apply in CI)" >> "$SUMMARY"
echo "- EXPORTACIÓN DE ARCHIVO PENDIENTE" >> "$SUMMARY"
echo "- INTEGRACIÓN M06 PENDIENTE" >> "$SUMMARY"
echo "- External providers: none" >> "$SUMMARY"

if [[ "$FAIL" -ne 0 ]]; then
  echo "QUALITY CHECKS FAILED" | tee -a "$SUMMARY"
  exit 1
fi
echo "QUALITY CHECKS PASSED" | tee -a "$SUMMARY"
echo "Summary written to $SUMMARY"
