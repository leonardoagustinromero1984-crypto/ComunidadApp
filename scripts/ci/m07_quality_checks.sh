#!/usr/bin/env bash
# LeoVer M07 Etapa 4 — local CI quality checks (no external SaaS).
set -euo pipefail
ROOT="${ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
MIG="$ROOT/supabase/migrations"
export ROOT
FAIL=0

echo "== Migration numbering =="
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
echo "Highest migration: $(echo "$nums" | tail -n1)"

echo "== Kotlin↔SQL event catalog exact compare (029) =="
python3 - <<'PY'
import pathlib, re, sys, os
root = pathlib.Path(os.environ['ROOT']).resolve()
sql = (root / 'supabase/migrations/029_m07_observability_audit_security_error_foundation.sql').read_text(encoding='utf-8')
kt = (root / 'app/src/main/java/com/comunidapp/app/domain/observability/catalog/ObservabilityEventCatalog.kt').read_text(encoding='utf-8')
sql_keys = set(re.findall(r"'(m0[0-7]\.[a-z0-9_]+\.[a-z0-9_]+)'", sql))
kt_keys = set(re.findall(r'e\("(m0[0-7]\.[a-z0-9_]+\.[a-z0-9_]+)"', kt))
missing_sql = sorted(kt_keys - sql_keys)
extra_sql = sorted(sql_keys - kt_keys)
print(f"kotlin={len(kt_keys)} sql={len(sql_keys)}")
if missing_sql or extra_sql or len(kt_keys) != 108:
    print('MISSING_IN_SQL', missing_sql[:20])
    print('EXTRA_IN_SQL', extra_sql[:20])
    sys.exit(1)
print('catalog exact OK')
PY

echo "== Metric catalog Kotlin↔SQL (030) =="
python3 - <<'PY'
import pathlib, re, sys, os
root = pathlib.Path(os.environ['ROOT']).resolve()
sql = (root / 'supabase/migrations/030_m07_operational_observability_health_metrics_incidents.sql').read_text(encoding='utf-8')
kt = (root / 'app/src/main/java/com/comunidapp/app/domain/observability/catalog/OperationalMetricCatalog.kt').read_text(encoding='utf-8')
sql_keys = set(re.findall(r"'(m0[0-7]\.[a-z0-9_]+\.[a-z0-9_]+)'", sql))
kt_keys = set(re.findall(r'def\("(m0[0-7]\.[a-z0-9_]+\.[a-z0-9_]+)"', kt))
missing = sorted(kt_keys - sql_keys)
if missing:
    print('metric keys missing in SQL', missing)
    sys.exit(1)
if len(kt_keys) != 28:
    print('expected 28 kotlin metrics', len(kt_keys))
    sys.exit(1)
print('metric catalog OK', len(kt_keys))
PY

echo "== Local secret pattern scan =="
if grep -RInE 'service_role_key\s*=|BEGIN PRIVATE KEY|SUPABASE_SERVICE_ROLE_KEY=[A-Za-z0-9]' \
  --include='*.sql' --include='*.kt' --include='*.kts' --include='*.yml' \
  "$ROOT/supabase/migrations" "$ROOT/app/src" "$ROOT/.github" 2>/dev/null; then
  echo "Potential secret pattern found"
  FAIL=1
else
  echo "No embedded secret patterns found in scanned paths"
fi

echo "== Basic SQL lint (balanced begin/commit on 030) =="
python3 - <<'PY'
import pathlib, os, sys
root = pathlib.Path(os.environ['ROOT']).resolve()
text = (root / 'supabase/migrations/030_m07_operational_observability_health_metrics_incidents.sql').read_text(encoding='utf-8').lower()
if text.count('begin;') < 1 or text.count('commit;') < 1:
    print('030 missing begin/commit')
    sys.exit(1)
if 'analytics_events' in text:
    print('analytics table forbidden')
    sys.exit(1)
print('sql basic OK')
PY

if [[ "$FAIL" -ne 0 ]]; then
  echo "QUALITY CHECKS FAILED"
  exit 1
fi
echo "QUALITY CHECKS PASSED"
