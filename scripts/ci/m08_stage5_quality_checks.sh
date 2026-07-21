#!/usr/bin/env bash
# LeoVer M08 Etapa 5 — UI responsables/autorizaciones/transferencias quality gate.
# Static/local checks only: never applies remote SQL, never touches production.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" 2>/dev/null && pwd || true)"
if [[ -z "${ROOT:-}" ]]; then
  if [[ -n "$SCRIPT_DIR" && -d "$SCRIPT_DIR/../../app" ]]; then
    ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
  else
    ROOT="$(pwd)"
  fi
fi
MIG="$ROOT/supabase/migrations"
APP="$ROOT/app/src/main/java/com/comunidapp/app"
TESTS="$ROOT/app/src/test/java/com/comunidapp/app"
FAIL=0

fail() {
  echo "FAIL: $*"
  FAIL=1
}

require_file() {
  [[ -f "$1" ]] && echo "OK file: ${1#"$ROOT"/}" || fail "missing $1"
}

require_grep() {
  local pattern="$1" file="$2" label="$3"
  grep -qF "$pattern" "$file" && echo "OK $label" || fail "$label missing in ${file#"$ROOT"/}"
}

echo "== M08 Etapa 5 quality checks =="

echo "== Stage 5 files present =="
STAGE5_MAIN=(
  "$APP/viewmodel/PetResponsibilitiesViewModel.kt"
  "$APP/viewmodel/PetAuthorizationsViewModel.kt"
  "$APP/viewmodel/PetTransfersViewModel.kt"
  "$APP/ui/screens/pets/PetResponsibilitiesScreen.kt"
  "$APP/ui/screens/pets/PetAuthorizationsScreen.kt"
  "$APP/ui/screens/pets/PetTransfersScreen.kt"
  "$APP/ui/screens/pets/PetTransferDetailScreen.kt"
)
for f in "${STAGE5_MAIN[@]}"; do
  require_file "$f"
done
STAGE5_TESTS=(
  "$TESTS/viewmodel/PetResponsibilitiesViewModelTest.kt"
  "$TESTS/viewmodel/PetAuthorizationsViewModelTest.kt"
  "$TESTS/viewmodel/PetTransfersViewModelTest.kt"
  "$TESTS/viewmodel/M08Stage5StaticGuardsTest.kt"
  "$TESTS/viewmodel/M08Stage5Fakes.kt"
)
for f in "${STAGE5_TESTS[@]}"; do
  require_file "$f"
done

echo "== Routes and navigation wiring =="
NAVROUTES="$APP/navigation/NavRoutes.kt"
NAVGRAPH="$APP/navigation/ComunidappNavGraph.kt"
require_grep 'pet_responsibilities/{petId}' "$NAVROUTES" "PET_RESPONSIBILITIES route"
require_grep 'pet_authorizations/{petId}' "$NAVROUTES" "PET_AUTHORIZATIONS route"
require_grep 'pet_transfers/{petId}' "$NAVROUTES" "PET_TRANSFERS route"
require_grep 'pet_transfer_detail/{petId}/{transferId}' "$NAVROUTES" "PET_TRANSFER_DETAIL route"
require_grep 'PetResponsibilitiesViewModel.factory(petId)' "$NAVGRAPH" "responsibilities factory wiring"
require_grep 'PetAuthorizationsViewModel.factory(petId)' "$NAVGRAPH" "authorizations factory wiring"
require_grep 'PetTransfersViewModel.factory(petId)' "$NAVGRAPH" "transfers factory wiring"
require_grep 'PetTransferDetailScreen(' "$NAVGRAPH" "transfer detail wiring"

echo "== ViewModels use domain repositories and capability gating =="
require_grep 'PetResponsibilityRepository' "$APP/viewmodel/PetResponsibilitiesViewModel.kt" "responsibility repo usage"
require_grep 'canManageResponsibilities' "$APP/viewmodel/PetResponsibilitiesViewModel.kt" "responsibility capability gate"
require_grep 'PetAuthorizationRepository' "$APP/viewmodel/PetAuthorizationsViewModel.kt" "authorization repo usage"
require_grep 'canManageAuthorizations' "$APP/viewmodel/PetAuthorizationsViewModel.kt" "authorization capability gate"
require_grep 'PetTransferRepository' "$APP/viewmodel/PetTransfersViewModel.kt" "transfer repo usage"
require_grep 'canInitiateTransfer' "$APP/viewmodel/PetTransfersViewModel.kt" "transfer capability gate"
require_grep 'searchPublicProfiles' "$APP/viewmodel/PetResponsibilitiesViewModel.kt" "controlled person search"

echo "== No ownerId-equality authorization in stage 5 sources =="
# Comparisons like `ownerId == uid` are forbidden; nullable projections are fine.
if grep -n 'ownerId ==\|== ownerId\|ownerId!!' \
  "${STAGE5_MAIN[@]}" \
  "$APP/viewmodel/PetDetailViewModel.kt" 2>/dev/null; then
  fail "ownerId equality authorization found in stage 5 sources"
else
  echo "OK no ownerId equality authorization"
fi

echo "== No RPC / Supabase client access from Composables =="
if grep -n 'postgrest\|\.rpc(\|supabase\.' \
  "$APP/ui/screens/pets/PetResponsibilitiesScreen.kt" \
  "$APP/ui/screens/pets/PetAuthorizationsScreen.kt" \
  "$APP/ui/screens/pets/PetTransfersScreen.kt" \
  "$APP/ui/screens/pets/PetTransferDetailScreen.kt" 2>/dev/null; then
  fail "direct RPC/Supabase access found in stage 5 composables"
else
  echo "OK no RPC from composables"
fi

echo "== No GlobalScope in stage 5 sources =="
if grep -n 'GlobalScope' "${STAGE5_MAIN[@]}" 2>/dev/null; then
  fail "GlobalScope found in stage 5 sources"
else
  echo "OK no GlobalScope"
fi

echo "== Authorization allowlist never grants forbidden capabilities =="
AUTHVM="$APP/viewmodel/PetAuthorizationsViewModel.kt"
if awk '/GRANTABLE_CAPABILITIES: Set<PetCapability> = setOf\(/,/\)/' "$AUTHVM" \
  | grep -q 'INITIATE_TRANSFER\|MARK_DECEASED\|PetCapability.ARCHIVE\|MANAGE_RESPONSIBILITIES'; then
  fail "forbidden capability inside GRANTABLE_CAPABILITIES allowlist"
else
  echo "OK allowlist excludes transfer/deceased/archive/manage_responsibilities"
fi

echo "== Android source credential guard =="
if grep -RIn \
  --include='*.kt' --include='*.kts' --include='*.xml' --include='*.properties' \
  -E '(service_role_key|SUPABASE_SERVICE_ROLE|service_role[[:space:]]*[:=])' \
  "$ROOT/app/src/main" 2>/dev/null; then
  fail "service role credential usage found in app/src/main"
else
  echo "OK no service role credential usage"
fi

echo "== Migration inventory: max 036, no 037, unchanged =="
require_file "$MIG/035_m08_pets_responsibilities_and_rls.sql"
require_file "$MIG/036_m08_pet_repository_compatibility_rpcs.sql"
if compgen -G "$MIG/037_*.sql" >/dev/null; then
  fail "migration 037 must be absent"
else
  echo "OK no migration 037"
fi
if git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  # Prefer git.exe on WSL/NTFS mounts (status --porcelain false-dirties CRLF files).
  # Fall back to content diff ignoring CR-at-EOL so line-ending noise is not a failure.
  GIT_BIN=git
  if command -v git.exe >/dev/null 2>&1; then
    GIT_BIN=git.exe
  fi
  dirty=$(
    {
      "$GIT_BIN" -C "$ROOT" diff --ignore-cr-at-eol --name-only -- supabase/migrations/ 2>/dev/null || true
      "$GIT_BIN" -C "$ROOT" diff --cached --ignore-cr-at-eol --name-only -- supabase/migrations/ 2>/dev/null || true
    } | sed 's/\r$//' | awk 'NF { print }'
  )
  if [[ -z "${dirty}" ]]; then
    echo "OK migrations unchanged"
  else
    echo "$dirty"
    fail "supabase migrations were modified"
  fi
fi

echo "== Backlog de defectos 4D presente y sin PASS inventado =="
BACKLOG="$ROOT/docs/04-calidad/M08-backlog-defectos-smoke-staging.md"
require_file "$BACKLOG"
require_grep 'M08-SMOKE-001' "$BACKLOG" "M08-SMOKE-001 entry"
require_grep 'BACKLOG' "$BACKLOG" "backlog state"
require_grep 'OTROS DEFECTOS PENDIENTES DE CLASIFICACIÓN' "$BACKLOG" "pending classification section"
# Match declared status lines only (avoid false positives from prose that forbids PASS).
if grep -E '^[[:space:]]*(SMOKE APK STAGING|SMOKE MANUAL|SMOKE INTEGRAL M08)[[:space:]]*—[[:space:]]*PASS[[:space:]]*$' "$BACKLOG"; then
  fail "backlog must not declare smoke PASS"
else
  echo "OK backlog does not declare smoke PASS"
fi

echo "== Stage 5 documentation =="
DOCS=(
  "$ROOT/docs/02-arquitectura/M08-etapa-5-ui-responsables-transferencias.md"
  "$ROOT/docs/04-calidad/M08-reporte-validacion-etapa-5.md"
)
for doc in "${DOCS[@]}"; do
  require_file "$doc"
  require_grep 'SMOKE INTEGRAL M08 — PENDIENTE' "$doc" "smoke pending marker"
  require_grep 'PRODUCCIÓN NO MODIFICADA' "$doc" "production untouched marker"
  if grep -E '^[[:space:]]*(SMOKE INTEGRAL M08[[:space:]]*—[[:space:]]*PASS|ETAPA 4D[[:space:]]*—[[:space:]]*CERRADA)[[:space:]]*$' "$doc"; then
    fail "doc ${doc#"$ROOT"/} must not declare smoke PASS or 4D closed"
  fi
done

echo "== Production untouched (no production SQL scripts added) =="
if compgen -G "$ROOT/scripts/sql/*production*" >/dev/null; then
  fail "production SQL scripts must not exist"
else
  echo "OK no production SQL scripts"
fi

if [[ "$FAIL" -ne 0 ]]; then
  echo "M08 STAGE 5 QUALITY CHECKS FAILED"
  exit 1
fi
echo "M08 STAGE 5 QUALITY CHECKS PASSED"
