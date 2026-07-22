#!/usr/bin/env bash
# LeoVer M08 Etapa 6 — fallecimiento, duplicados y fotos quality gate.
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

echo "== M08 Etapa 6 quality checks =="

echo "== Stage 6 files present =="
STAGE6_MAIN=(
  "$APP/viewmodel/PetDetailViewModel.kt"
  "$APP/viewmodel/PetStatusHistoryViewModel.kt"
  "$APP/viewmodel/PetFormViewModel.kt"
  "$APP/ui/screens/pets/PetDetailScreen.kt"
  "$APP/ui/screens/pets/PetStatusHistoryScreen.kt"
  "$APP/ui/screens/pets/PetFormScreen.kt"
  "$APP/data/repository/PetRepository.kt"
  "$APP/data/repository/LegacyPetRepositoryAdapter.kt"
  "$APP/data/remote/supabase/m08/M08PetErrorMapper.kt"
)
for f in "${STAGE6_MAIN[@]}"; do
  require_file "$f"
done
STAGE6_TESTS=(
  "$TESTS/viewmodel/MarkPetDeceasedViewModelTest.kt"
  "$TESTS/viewmodel/PetStatusHistoryViewModelTest.kt"
  "$TESTS/viewmodel/M08Stage6StaticGuardsTest.kt"
)
for f in "${STAGE6_TESTS[@]}"; do
  require_file "$f"
done

echo "== Routes and navigation wiring =="
NAVROUTES="$APP/navigation/NavRoutes.kt"
NAVGRAPH="$APP/navigation/ComunidappNavGraph.kt"
require_grep 'pet_status_history/{petId}' "$NAVROUTES" "PET_STATUS_HISTORY route"
require_grep 'fun petStatusHistory(petId: String)' "$NAVROUTES" "petStatusHistory helper"
require_grep 'PetStatusHistoryViewModel.factory(petId)' "$NAVGRAPH" "status history factory wiring"
require_grep 'PetStatusHistoryScreen(' "$NAVGRAPH" "status history screen wiring"
# Stage 5 routes intact
require_grep 'pet_responsibilities/{petId}' "$NAVROUTES" "PET_RESPONSIBILITIES route intact"
require_grep 'pet_transfers/{petId}' "$NAVROUTES" "PET_TRANSFERS route intact"

echo "== Repository stage 6 surface =="
require_grep 'fun markPetDeceased' "$APP/data/repository/PetRepository.kt" "markPetDeceased"
require_grep 'fun restorePet' "$APP/data/repository/PetRepository.kt" "restorePet"
require_grep 'fun listStatusHistory' "$APP/data/repository/PetRepository.kt" "listStatusHistory"
require_grep 'fun detectDuplicateCandidates' "$APP/data/repository/PetRepository.kt" "detectDuplicateCandidates"
require_grep 'remote.markPetDeceased' "$APP/data/repository/LegacyPetRepositoryAdapter.kt" "adapter markDeceased"
require_grep 'remote.restorePet' "$APP/data/repository/LegacyPetRepositoryAdapter.kt" "adapter restore"
require_grep 'remote.detectDuplicates' "$APP/data/repository/LegacyPetRepositoryAdapter.kt" "adapter duplicates"

echo "== Error mapper stage 6 codes =="
MAPPER="$APP/data/remote/supabase/m08/M08PetErrorMapper.kt"
for code in PET_ALREADY_DECEASED PET_ALREADY_ARCHIVED PET_DECEASED_CANNOT_ARCHIVE \
  PET_DECEASED_CANNOT_RESTORE PET_NOT_ARCHIVED PET_AVATAR_ASSET_NOT_FOUND \
  PET_AVATAR_PURPOSE_INVALID; do
  require_grep "$code" "$MAPPER" "mapper $code"
done
require_grep 'Ya existe una mascota activa registrada con ese microchip.' "$MAPPER" \
  "microchip conflict UX message"

echo "== Avatar flow (no photo_url writes) =="
FORMVM="$APP/viewmodel/PetFormViewModel.kt"
require_grep 'FileAssetPurpose.PET_AVATAR' "$FORMVM" "PET_AVATAR upload"
require_grep 'setPetAvatarAsset' "$FORMVM" "setPetAvatarAsset"
require_grep 'canManageMedia' "$FORMVM" "media capability gate"
require_grep 'detectDuplicateCandidates' "$FORMVM" "duplicate detection"
if grep -n 'photo_url' "$FORMVM" "$APP/ui/screens/pets/PetFormScreen.kt" \
  "$APP/ui/screens/pets/PetDetailScreen.kt" 2>/dev/null; then
  fail "photo_url column write/reference found in stage 6 form/detail sources"
else
  echo "OK no photo_url in stage 6 form/detail sources"
fi

echo "== No ownerId-equality authorization in stage 6 sources =="
if grep -n 'ownerId ==\|== ownerId\|ownerId!!' \
  "$APP/viewmodel/PetDetailViewModel.kt" \
  "$APP/viewmodel/PetStatusHistoryViewModel.kt" \
  "$APP/viewmodel/PetFormViewModel.kt" \
  "$APP/ui/screens/pets/PetDetailScreen.kt" \
  "$APP/ui/screens/pets/PetStatusHistoryScreen.kt" 2>/dev/null; then
  fail "ownerId equality authorization found in stage 6 sources"
else
  echo "OK no ownerId equality authorization"
fi

echo "== No RPC / Supabase client access from Composables =="
if grep -n 'postgrest\|\.rpc(\|supabase\.' \
  "$APP/ui/screens/pets/PetDetailScreen.kt" \
  "$APP/ui/screens/pets/PetStatusHistoryScreen.kt" \
  "$APP/ui/screens/pets/PetFormScreen.kt" 2>/dev/null; then
  fail "direct RPC/Supabase access found in stage 6 composables"
else
  echo "OK no RPC from composables"
fi

echo "== No GlobalScope in stage 6 sources =="
if grep -n 'GlobalScope' \
  "$APP/viewmodel/PetDetailViewModel.kt" \
  "$APP/viewmodel/PetStatusHistoryViewModel.kt" \
  "$APP/viewmodel/PetFormViewModel.kt" 2>/dev/null; then
  fail "GlobalScope found in stage 6 sources"
else
  echo "OK no GlobalScope"
fi

echo "== Gallery: allow documented backlog if screen absent =="
GALLERY_SCREEN="$APP/ui/screens/pets/PetPhotoGalleryScreen.kt"
AUDIT="$ROOT/docs/02-arquitectura/M08-etapa-6-auditoria-contratos.md"
if [[ -f "$GALLERY_SCREEN" ]]; then
  echo "OK gallery screen present"
  require_grep 'canManageMedia' "$GALLERY_SCREEN" "gallery media gate" || true
else
  require_file "$AUDIT"
  require_grep 'GALLERY GAP' "$AUDIT" "gallery gap documented"
  require_grep 'BACKLOG' "$AUDIT" "gallery backlog marker"
  echo "OK gallery deferred as documented backlog"
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

echo "== Backlog de defectos presente y sin PASS inventado =="
BACKLOG="$ROOT/docs/04-calidad/M08-backlog-defectos-smoke-staging.md"
require_file "$BACKLOG"
require_grep 'M08-SMOKE-001' "$BACKLOG" "M08-SMOKE-001 entry"
require_grep 'BACKLOG' "$BACKLOG" "backlog state"
# Match declared status lines only (avoid false positives from prose that forbids PASS).
if grep -E '^[[:space:]]*(SMOKE APK STAGING|SMOKE MANUAL|SMOKE INTEGRAL M08)[[:space:]]*—[[:space:]]*PASS[[:space:]]*$' "$BACKLOG"; then
  fail "backlog must not declare smoke PASS"
else
  echo "OK backlog does not declare smoke PASS"
fi

echo "== Stage 6 documentation =="
DOCS=(
  "$ROOT/docs/02-arquitectura/M08-etapa-6-auditoria-contratos.md"
  "$ROOT/docs/02-arquitectura/M08-etapa-6-fallecimiento-duplicados-fotos.md"
  "$ROOT/docs/04-calidad/M08-reporte-validacion-etapa-6.md"
)
for doc in "${DOCS[@]}"; do
  require_file "$doc"
  require_grep 'SMOKE INTEGRAL M08 — PENDIENTE' "$doc" "smoke pending marker"
  require_grep 'PRODUCCIÓN NO MODIFICADA' "$doc" "production untouched marker"
  if grep -E '^[[:space:]]*(SMOKE INTEGRAL M08[[:space:]]*—[[:space:]]*PASS)[[:space:]]*$' "$doc"; then
    fail "doc ${doc#"$ROOT"/} must not declare smoke PASS"
  fi
done

echo "== Production untouched (no production SQL scripts added) =="
if compgen -G "$ROOT/scripts/sql/*production*" >/dev/null; then
  fail "production SQL scripts must not exist"
else
  echo "OK no production SQL scripts"
fi

if [[ "$FAIL" -ne 0 ]]; then
  echo "M08 STAGE 6 QUALITY CHECKS FAILED"
  exit 1
fi
echo "M08 STAGE 6 QUALITY CHECKS PASSED"
