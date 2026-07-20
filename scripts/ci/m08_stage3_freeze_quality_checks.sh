#!/usr/bin/env bash
# LeoVer M08 Etapa 3A — freeze quality gate (documentación). Sin SQL ejecutable.
set -euo pipefail
ROOT="${ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
FAIL=0

echo "== M08 Stage 3A freeze quality =="

require_file() {
  local f="$1"
  if [[ ! -f "$f" ]]; then
    echo "MISSING: $f"
    FAIL=1
    return
  fi
  local sz
  sz=$(wc -c <"$f" | tr -d ' ')
  if [[ "$sz" -lt 400 ]]; then
    echo "TOO_SMALL: $f ($sz bytes)"
    FAIL=1
  else
    echo "OK file: $f ($sz bytes)"
  fi
}

require_file "$ROOT/docs/02-arquitectura/M08-etapa-3-freeze-esquema-rls.md"
require_file "$ROOT/docs/02-arquitectura/M08-esquema-propuesto-migracion-035.md"
require_file "$ROOT/docs/04-calidad/M08-plan-validacion-migracion-035.md"
require_file "$ROOT/docs/04-calidad/M08-matriz-rls-y-permisos.md"

echo "== Required decision markers =="
for pair in \
  "docs/02-arquitectura/M08-etapa-3-freeze-esquema-rls.md|owner_id" \
  "docs/02-arquitectura/M08-etapa-3-freeze-esquema-rls.md|microchip" \
  "docs/02-arquitectura/M08-esquema-propuesto-migracion-035.md|pet_responsibilities" \
  "docs/02-arquitectura/M08-esquema-propuesto-migracion-035.md|m08_accept_pet_transfer" \
  "docs/02-arquitectura/M08-esquema-propuesto-migracion-035.md|BACKFILL" \
  "docs/02-arquitectura/M08-esquema-propuesto-migracion-035.md|Rollback" \
  "docs/04-calidad/M08-matriz-rls-y-permisos.md|SELECT" \
  "docs/04-calidad/M08-matriz-rls-y-permisos.md|pet.read" \
  "docs/04-calidad/M08-plan-validacion-migracion-035.md|db reset"
do
  f="$ROOT/${pair%%|*}"
  needle="${pair#*|}"
  if ! grep -qi "$needle" "$f"; then
    echo "MISSING marker '$needle' in $f"
    FAIL=1
  fi
done
echo "OK markers"

echo "== Migration max 034 / no 035 file =="
MIG="$ROOT/supabase/migrations"
highest=$(ls "$MIG" | grep -E '^[0-9]{3}_' | sed 's/_.*//' | sort | tail -n1)
echo "Highest migration: $highest"
if [[ "$highest" != "034" ]]; then
  echo "Expected highest 034, got $highest"
  FAIL=1
fi
if ls "$MIG"/035_* >/dev/null 2>&1; then
  echo "FORBIDDEN: supabase/migrations/035_* exists"
  FAIL=1
fi

echo "== No new executable SQL under migrations (035 absent already) =="
# Ensure this branch did not add any *.sql outside docs (migrations folder unchanged vs expected 34)
count=$(ls "$MIG"/*.sql 2>/dev/null | wc -l | tr -d ' ')
if [[ "$count" != "34" ]]; then
  echo "Expected 34 migration sql files, got $count"
  FAIL=1
else
  echo "OK migration count=34"
fi

echo "== App / UI / supabase config not required changed for 3A (presence of legacy UI) =="
require_file "$ROOT/app/src/main/java/com/comunidapp/app/ui/screens/pets/MyPetsScreen.kt"
require_file "$ROOT/app/src/main/java/com/comunidapp/app/domain/pets/PetModels.kt"

if [[ "$FAIL" -ne 0 ]]; then
  echo "M08 STAGE 3A FREEZE QUALITY CHECKS FAILED"
  exit 1
fi
echo "M08 STAGE 3A FREEZE QUALITY CHECKS PASSED"
exit 0
