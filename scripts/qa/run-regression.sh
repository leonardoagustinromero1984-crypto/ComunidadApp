#!/usr/bin/env bash
# Linux Cloud regression runner for LeoVer.
# Fast baseline (default) runs JVM unit tests only.
# Does not start an emulator, Maestro, PowerShell, or gradlew.bat.
# Configuration cache stays enabled for the product; this runner passes
# --no-configuration-cache because Gradle 9.4.1 cannot serialize localDebug tasks.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

MODE="fast"
if [[ $# -gt 0 ]]; then
  case "$1" in
    --fast|"") MODE="fast" ;;
    --affected) MODE="affected" ;;
    --full|--core) MODE="fast" ;;
    --maestro|--emulator|--smoke-e2e|--full-e2e)
      echo "MAESTRO: NOT RUN"
      echo "EMULATOR: NOT RUN"
      echo "This Linux runner does not start Maestro or an emulator."
      exit 2
      ;;
    -h|--help)
      cat <<'EOF'
Usage: scripts/qa/run-regression.sh [--fast|--affected|--full]

  --fast (default)  :app:testLocalDebugUnitTest and :shared:testAndroidHostTest
  --affected        Gradle --tests from docs/qa/change-impact-map.yaml
  --full            same JVM baseline as --fast
  --maestro         refused (not started)
EOF
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 2
      ;;
  esac
fi

if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
  for path in \
    /usr/lib/jvm/java-21-openjdk-amd64 \
    /usr/lib/jvm/java-21-openjdk-arm64 \
    /usr/lib/jvm/temurin-21-jdk-amd64
  do
    if [[ -x "$path/bin/java" ]]; then
      export JAVA_HOME="$path"
      break
    fi
  done
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi

if [[ -z "${ANDROID_HOME:-}" ]]; then
  if [[ -d "${HOME}/Android/Sdk" ]]; then
    export ANDROID_HOME="${HOME}/Android/Sdk"
  fi
fi
if [[ -n "${ANDROID_HOME:-}" ]]; then
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
fi

chmod +x "${REPO_ROOT}/gradlew"
GRADLE=("${REPO_ROOT}/gradlew" --no-configuration-cache --no-daemon)

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="${REPO_ROOT}/artifacts/qa/regression/${STAMP}"
mkdir -p "$OUT_DIR"
REPORT="${OUT_DIR}/report.md"

run_gradle() {
  echo "GRADLE $*"
  "${GRADLE[@]}" "$@"
}

AFFECTED_FILTERS=()
AFFECTED_MODULES=""
AFFECTED_SHARED="no"
COVERAGE="OK"

if [[ "$MODE" == "affected" ]]; then
  MAP_JSON="$(python3 - "$REPO_ROOT" <<'PY'
import json, subprocess, sys
from pathlib import Path
root = Path(sys.argv[1])
map_path = root / "docs/qa/change-impact-map.yaml"
text = map_path.read_text()
ignore = []
blocks = []
current = None
section = None
for raw in text.splitlines():
    line = raw.rstrip()
    if line.startswith("ignore_prefixes:"):
        section = "ignore"
        current = None
        continue
    if line.startswith("mappings:"):
        section = "mappings"
        continue
    if section == "ignore" and line.strip().startswith("- "):
        ignore.append(line.strip()[2:].strip().strip("/"))
        continue
    if section != "mappings":
        continue
    stripped = line.strip()
    if stripped.startswith("- id:"):
        if current:
            blocks.append(current)
        current = {"id": stripped.split(":", 1)[1].strip(), "paths": [], "modules": [], "gradle": [], "maestro": False, "field": None}
        continue
    if current is None:
        continue
    if stripped.startswith("paths:"):
        current["field"] = "paths"
        continue
    if stripped.startswith("modules:"):
        current["field"] = "modules"
        if "[" in stripped:
            inner = stripped.split("[", 1)[1].split("]", 1)[0]
            current["modules"].extend([p.strip() for p in inner.split(",") if p.strip()])
        continue
    if stripped.startswith("gradle_tests:"):
        current["field"] = "gradle"
        continue
    if stripped.startswith("suites:"):
        current["field"] = None
        continue
    if stripped.startswith("maestro_required:"):
        current["maestro"] = stripped.endswith("true")
        current["field"] = None
        continue
    if stripped.startswith("- ") and current.get("field"):
        val = stripped[2:].strip()
        current[current["field"]].append(val)
if current:
    blocks.append(current)

def ignored(rel):
    for prefix in ignore:
        if rel == prefix or rel.startswith(prefix + "/"):
            return True
    return False

def matches(rel, pattern):
    pat = pattern.replace("\\", "/").rstrip("/")
    if rel == pat or rel.startswith(pat + "/"):
        return True
    if pat.endswith("**"):
        stem = pat[:-2].rstrip("/")
        return rel.startswith(stem)
    return False

changed = []
for args in (["diff", "--name-only"], ["diff", "--name-only", "--cached"], ["ls-files", "--others", "--exclude-standard"]):
    out = subprocess.run(["git", "-C", str(root), *args], capture_output=True, text=True)
    for line in out.stdout.splitlines():
        rel = line.strip().replace("\\", "/")
        if rel and not rel.startswith("warning:"):
            changed.append(rel)
changed = list(dict.fromkeys(changed))
functional = ("app/src/main/", "shared/src/commonMain/", "shared/src/androidMain/", "shared/src/jvmMain/", "infra/supabase-canonical/supabase/migrations/")
modules, gradle, unmapped, shared = [], [], [], False
maestro = False
for rel in changed:
    if ignored(rel):
        continue
    if rel.startswith("shared/"):
        shared = True
    hit = None
    for block in blocks:
        if any(matches(rel, p) for p in block["paths"]):
            hit = block
            break
    if hit:
        for m in hit["modules"]:
            if m not in modules:
                modules.append(m)
        for g in hit["gradle"]:
            if g not in gradle:
                gradle.append(g)
        maestro = maestro or hit["maestro"]
    elif rel.startswith(functional):
        unmapped.append(rel)
print(json.dumps({
    "modules": modules,
    "gradle": gradle,
    "unmapped": unmapped,
    "maestro": maestro,
    "shared": shared,
    "coverage": "MISSING" if unmapped else "OK",
}))
PY
)"
  echo "$MAP_JSON" > "${OUT_DIR}/coverage.json"
  COVERAGE="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["coverage"])' <<<"$MAP_JSON")"
  AFFECTED_MODULES="$(python3 -c 'import json,sys; print(", ".join(json.load(sys.stdin)["modules"]))' <<<"$MAP_JSON")"
  AFFECTED_SHARED="$(python3 -c 'import json,sys; print("yes" if json.load(sys.stdin)["shared"] else "no")' <<<"$MAP_JSON")"
  mapfile -t AFFECTED_FILTERS < <(python3 -c 'import json,sys; print("\n".join(json.load(sys.stdin)["gradle"]))' <<<"$MAP_JSON")
  if [[ "$COVERAGE" == "MISSING" ]]; then
    echo "COVERAGE: MISSING"
    python3 -c 'import json,sys; print("\n".join(json.load(sys.stdin)["unmapped"]))' <<<"$MAP_JSON"
    echo "QA_REGRESSION_COVERAGE_MISSING" >&2
    exit 1
  fi
fi

APP_STATUS="NOT RUN"
SHARED_STATUS="NOT RUN"
set +e
if [[ "$MODE" == "affected" && ${#AFFECTED_FILTERS[@]} -gt 0 ]]; then
  ARGS=(:app:testLocalDebugUnitTest)
  for filter in "${AFFECTED_FILTERS[@]}"; do
    [[ -n "$filter" ]] || continue
    ARGS+=(--tests "$filter")
  done
  run_gradle "${ARGS[@]}"
  APP_CODE=$?
  if [[ "$AFFECTED_SHARED" == "yes" ]]; then
    run_gradle :shared:testAndroidHostTest
    SHARED_CODE=$?
  else
    SHARED_CODE=0
    SHARED_STATUS="SKIPPED"
  fi
else
  run_gradle :app:testLocalDebugUnitTest
  APP_CODE=$?
  run_gradle :shared:testAndroidHostTest
  SHARED_CODE=$?
fi
set -e

if [[ "$APP_STATUS" != "SKIPPED" ]]; then
  APP_STATUS="$([[ $APP_CODE -eq 0 ]] && echo PASS || echo FAIL)"
fi
if [[ "$SHARED_STATUS" != "SKIPPED" ]]; then
  SHARED_STATUS="$([[ $SHARED_CODE -eq 0 ]] && echo PASS || echo FAIL)"
fi

HEAD="$(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)"
DIRTY="$(git -C "$REPO_ROOT" status --porcelain 2>/dev/null | wc -l | tr -d ' ')"

cat > "$REPORT" <<EOF
# LeoVer regression ${STAMP}

HEAD: ${HEAD}
DIRTY_FILES: ${DIRTY}
MODE: ${MODE}
MODULES: ${AFFECTED_MODULES}
COVERAGE: ${COVERAGE}
APP testLocalDebugUnitTest: ${APP_STATUS}
SHARED testAndroidHostTest: ${SHARED_STATUS}
CONFIGURATION CACHE: disabled for this runner only (--no-configuration-cache)
EMULATOR: NOT RUN
MAESTRO: NOT RUN
EOF

echo "REPORT=${REPORT}"
echo "APP: ${APP_STATUS}"
echo "SHARED: ${SHARED_STATUS}"
echo "EMULATOR: NOT RUN"
echo "MAESTRO: NOT RUN"

if [[ "$APP_STATUS" == "FAIL" || "$SHARED_STATUS" == "FAIL" ]]; then
  exit 1
fi
echo "CLOUD REGRESSION: PASS"
