#!/usr/bin/env bash
# LeoVer M08 Etapa 2 — quality gate (local). Sin Supabase remoto.
set -euo pipefail
ROOT="${ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
FAIL=0

echo "== M08 Stage 2 quality =="

require_file() {
  if [[ ! -f "$1" ]]; then
    echo "MISSING: $1"
    FAIL=1
  else
    echo "OK file: $1"
  fi
}

require_file "$ROOT/docs/02-arquitectura/M08-etapa-2-contratos-y-permisos.md"
require_file "$ROOT/app/src/main/java/com/comunidapp/app/domain/pets/PetModels.kt"
require_file "$ROOT/app/src/main/java/com/comunidapp/app/domain/pets/PetEnums.kt"
require_file "$ROOT/app/src/main/java/com/comunidapp/app/domain/pets/PetDomainRepositories.kt"
require_file "$ROOT/app/src/main/java/com/comunidapp/app/domain/pets/PetCapabilityMatrix.kt"
require_file "$ROOT/app/src/main/java/com/comunidapp/app/domain/pets/PetAggregateRules.kt"
require_file "$ROOT/app/src/main/java/com/comunidapp/app/domain/pets/PetTransferAndAuthorizationRules.kt"
require_file "$ROOT/app/src/main/java/com/comunidapp/app/domain/pets/authorization/PetAuthorizationBridge.kt"
require_file "$ROOT/app/src/test/java/com/comunidapp/app/domain/pets/PetDomainStage2Test.kt"

echo "== Capability catalog present =="
if ! grep -q 'enum class PetCapability' "$ROOT/app/src/main/java/com/comunidapp/app/domain/pets/PetEnums.kt"; then
  echo "PetCapability enum missing"
  FAIL=1
fi
if ! grep -q 'PET_READ("pet.read")' "$ROOT/app/src/main/java/com/comunidapp/app/domain/authorization/Authorization.kt"; then
  echo "PermissionCode pet.read missing"
  FAIL=1
fi

echo "== Migration numbering (max 034–036 across M08 stages; no 037) =="
MIG="$ROOT/supabase/migrations"
highest=$(ls "$MIG" | grep -E '^[0-9]{3}_' | sed 's/_.*//' | sort | tail -n1)
echo "Highest migration: $highest"
if [[ "$highest" != "034" && "$highest" != "035" && "$highest" != "036" ]]; then
  echo "Expected highest migration 034–036, got $highest"
  FAIL=1
fi
if ls "$MIG"/037_* >/dev/null 2>&1; then
  echo "Migration 037 must not exist"
  FAIL=1
fi

echo "== public.pets / migrations presence (001–034 intact; 035/036 allowed post-3B/3C) =="
require_file "$ROOT/supabase/migrations/001_initial_schema.sql"
if ! grep -q 'create table if not exists public.pets' "$ROOT/supabase/migrations/001_initial_schema.sql"; then
  echo "public.pets definition missing from 001"
  FAIL=1
fi

echo "== No Supabase/Android/Compose imports in pure M08 domain models =="
DOMAIN_PETS="$ROOT/app/src/main/java/com/comunidapp/app/domain/pets"
if grep -RInE 'io\.supabase|androidx\.|android\.|compose\.|firebase' "$DOMAIN_PETS" 2>/dev/null; then
  echo "Forbidden imports in domain/pets"
  FAIL=1
else
  echo "OK domain/pets imports"
fi

echo "== Legacy UI pets screens must still exist (untouched requirement) =="
require_file "$ROOT/app/src/main/java/com/comunidapp/app/ui/screens/pets/MyPetsScreen.kt"
require_file "$ROOT/app/src/main/java/com/comunidapp/app/ui/screens/pets/PetFormScreen.kt"
require_file "$ROOT/app/src/main/java/com/comunidapp/app/ui/screens/pets/PetDetailScreen.kt"
require_file "$ROOT/app/src/main/java/com/comunidapp/app/data/repository/PetRepository.kt"

if [[ "$FAIL" -ne 0 ]]; then
  echo "M08 STAGE 2 QUALITY CHECKS FAILED"
  exit 1
fi
echo "M08 STAGE 2 QUALITY CHECKS PASSED"
exit 0
