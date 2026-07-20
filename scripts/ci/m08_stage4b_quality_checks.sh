#!/usr/bin/env bash
# LeoVer M08 Etapa 4B — quality gate: repositorios + adaptador legacy (Android).
# No remotes. No --linked. No secrets. No migration 037.
set -euo pipefail
ROOT="${ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
FAIL=0
MIG="$ROOT/supabase/migrations"
M08_PKG="$ROOT/app/src/main/java/com/comunidapp/app/data/remote/supabase/m08"
ADAPTER="$ROOT/app/src/main/java/com/comunidapp/app/data/repository/LegacyPetRepositoryAdapter.kt"

echo "== M08 Stage 4B repositories / legacy adapter quality =="

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

require_file "$M08_PKG/PetM08Dtos.kt"
require_file "$M08_PKG/PetAccessContext.kt"
require_file "$M08_PKG/PetM08Mappers.kt"
require_file "$M08_PKG/M08PetErrorMapper.kt"
require_file "$M08_PKG/PetM08RemoteDataSource.kt"
require_file "$M08_PKG/SupabasePetM08RemoteDataSource.kt"
require_file "$ADAPTER"
require_file "$ROOT/app/src/main/java/com/comunidapp/app/data/repository/SupabasePetDomainRepository.kt"
require_file "$ROOT/app/src/main/java/com/comunidapp/app/data/repository/SupabasePetResponsibilityRepository.kt"
require_file "$ROOT/app/src/main/java/com/comunidapp/app/data/repository/SupabasePetAuthorizationRepository.kt"
require_file "$ROOT/app/src/main/java/com/comunidapp/app/data/repository/SupabasePetTransferRepository.kt"
require_file "$ROOT/docs/02-arquitectura/M08-etapa-4b-repositorios-adaptador-legacy.md"
require_file "$ROOT/docs/04-calidad/M08-reporte-validacion-etapa-4b.md"

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
    echo "$mod"
    echo "$staged"
    FAIL=1
  else
    echo "OK migrations clean"
  fi
fi

echo "== Adapter must not call legacy PetSupabaseDataSource mutate APIs =="
if grep -qF 'PetSupabaseDataSource' "$ADAPTER"; then
  echo "FORBIDDEN: LegacyPetRepositoryAdapter references PetSupabaseDataSource"
  FAIL=1
elif grep -Eiq 'from\([[:space:]]*"pets"[[:space:]]*\).*insert|from\([[:space:]]*"pets"[[:space:]]*\).*update|from\([[:space:]]*"pets"[[:space:]]*\).*delete|\.insert\(.*toPetRow|\.delete\([[:space:]]*\{' "$ADAPTER"; then
  echo "FORBIDDEN: LegacyPetRepositoryAdapter appears to mutate pets table directly"
  FAIL=1
else
  echo "OK adapter avoids direct pets mutate"
fi

echo "== Adapter / DS must use M08 RPCs =="
for needle in \
  m08_create_pet_with_principal \
  m08_update_pet_profile \
  m08_update_pet_health \
  m08_list_accessible_pets \
  m08_get_pet_access_context \
  m08_archive_pet \
  m08_set_pet_avatar_asset
do
  if ! grep -rqF "$needle" "$M08_PKG" "$ADAPTER"; then
    echo "MISSING RPC reference: $needle"
    FAIL=1
  fi
done

echo "== DataProvider wires LegacyPetRepositoryAdapter =="
DP="$ROOT/app/src/main/java/com/comunidapp/app/data/provider/DataProvider.kt"
if ! grep -qF 'LegacyPetRepositoryAdapter' "$DP"; then
  echo "MISSING LegacyPetRepositoryAdapter in DataProvider"
  FAIL=1
fi

echo "== Pet.ownerId nullable =="
PET_MODEL="$ROOT/app/src/main/java/com/comunidapp/app/data/model/Pet.kt"
if ! grep -qE 'ownerId:\s*String\?' "$PET_MODEL"; then
  echo "FORBIDDEN: Pet.ownerId must be String?"
  FAIL=1
else
  echo "OK Pet.ownerId nullable"
fi

echo "== Tests 4B present =="
require_file "$ROOT/app/src/test/java/com/comunidapp/app/data/remote/supabase/m08/LegacyPetRepositoryAdapterTest.kt"
require_file "$ROOT/app/src/test/java/com/comunidapp/app/data/remote/supabase/m08/FakePetM08RemoteDataSource.kt"

echo "== Pet ViewModels must not authorize via ownerId == auth =="
for f in \
  "$ROOT/app/src/main/java/com/comunidapp/app/viewmodel/PetDetailViewModel.kt" \
  "$ROOT/app/src/main/java/com/comunidapp/app/viewmodel/PetFormViewModel.kt"
do
  # Ignore comment lines; flag executable ACL comparisons only.
  if grep -E 'pet\.ownerId[[:space:]]*==|ownerId[[:space:]]*==[[:space:]].*auth|ownerId[[:space:]]*!=[[:space:]].*auth' "$f" \
    | grep -Ev '^[[:space:]]*(//|\*|/\*)' \
    | grep -Eq '.'; then
    echo "FORBIDDEN ownerId ACL in $f"
    FAIL=1
  fi
done
echo "OK pet ViewModels ACL"

echo "== No service_role in Android main sources =="
# Allow deny-list / sanitization catalog strings and explanatory comments only.
hits=$(grep -RIn --include='*.kt' -E 'service_role' "$ROOT/app/src/main" 2>/dev/null \
  | grep -vE 'MetadataAllowlist\.kt|Sanitization\.kt' \
  | grep -vE ':[0-9]+:[[:space:]]*//' \
  || true)
if [[ -n "$hits" ]]; then
  echo "$hits"
  echo "FORBIDDEN service_role in Android main"
  FAIL=1
else
  echo "OK no service_role (deny-lists/comments excluded)"
fi

echo "== Legacy PetSupabaseDataSource writes disabled =="
DS="$ROOT/app/src/main/java/com/comunidapp/app/data/remote/supabase/SupabaseDataSources.kt"
if grep -E 'from\(SupabaseTables\.PETS\)\.(insert|update|delete)' "$DS"; then
  echo "FORBIDDEN: PetSupabaseDataSource still mutates pets via PostgREST"
  FAIL=1
else
  echo "OK legacy pets mutate path disabled"
fi

echo "== PetFormViewModel must not put file_asset_id into photo_url =="
PFVM="$ROOT/app/src/main/java/com/comunidapp/app/viewmodel/PetFormViewModel.kt"
if grep -nE "photoUrl[[:space:]]*=" "$PFVM" | grep -Eiq "assetId|fileAssetId|file_asset"; then
  echo "FORBIDDEN: photoUrl assignment references asset id / file_asset"
  FAIL=1
else
  echo "OK photoUrl not fed file_asset_id"
fi
echo "== Docs staging block / Etapa 4B state =="
for f in \
  "$ROOT/docs/02-arquitectura/M08-etapa-4b-repositorios-adaptador-legacy.md" \
  "$ROOT/docs/04-calidad/M08-reporte-validacion-etapa-4b.md" \
  "$ROOT/docs/04-calidad/M08-matriz-operaciones-android-rpc-035.md" \
  "$ROOT/docs/03-modulos/M08-mascotas-y-responsables.md"
do
  grep -qF 'M08 ETAPA 4B — REPOSITORIOS Y ADAPTADOR LEGACY LISTOS LOCALMENTE' "$f" \
    || { echo "missing 4B LISTOS marker in $f"; FAIL=1; }
  grep -qi 'STAGING NO AUTORIZADO' "$f" || { echo "missing STAGING NO AUTORIZADO in $f"; FAIL=1; }
  grep -qi 'REQUIERE ETAPA 4C' "$f" || { echo "missing REQUIERE ETAPA 4C in $f"; FAIL=1; }
done

echo "== No remote / linked / secret patterns in validation+docs (scan only those dirs) =="
# Intentionally do NOT grep this script itself (avoids --linked false positive).
# Scan only etapa-4b / reporte-4b / module docs created for this stage (not historical M08 docs).
SCAN_FILES=(
  "$ROOT/docs/02-arquitectura/M08-etapa-4b-repositorios-adaptador-legacy.md"
  "$ROOT/docs/04-calidad/M08-reporte-validacion-etapa-4b.md"
  "$ROOT/docs/03-modulos/M08-mascotas-y-responsables.md"
  "$ROOT/docs/04-calidad/M08-matriz-operaciones-android-rpc-035.md"
)
for f in "${SCAN_FILES[@]}"; do
  if [[ -f "$f" ]]; then
    if grep -Eiq 'db push|--linked|migration repair|supabase\.co|eyJ[A-Za-z0-9_-]{10,}' "$f"; then
      echo "FORBIDDEN remote/linked/secret pattern in $f"
      FAIL=1
    fi
  fi
done
echo "OK no forbidden tokens in scanned 4B docs"

if [[ "$FAIL" -ne 0 ]]; then
  echo "M08 STAGE 4B QUALITY CHECKS FAILED"
  exit 1
fi
echo "M08 STAGE 4B QUALITY CHECKS PASSED"
exit 0
