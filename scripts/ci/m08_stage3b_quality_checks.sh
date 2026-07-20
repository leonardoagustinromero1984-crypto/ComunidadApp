#!/usr/bin/env bash
# LeoVer M08 Etapa 3B — quality gate: migración 035 local (+ docs/scripts).
# No remotes. No --linked. No secrets.
set -euo pipefail
ROOT="${ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
FAIL=0
MIG="$ROOT/supabase/migrations"
SQL035="$MIG/035_m08_pets_responsibilities_and_rls.sql"

echo "== M08 Stage 3B migration 035 quality =="

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

require_file "$SQL035"
require_file "$ROOT/scripts/sql/m08_validate_local_035.sql"
require_file "$ROOT/docs/02-arquitectura/M08-etapa-3b-migracion-035-local.md"
require_file "$ROOT/docs/04-calidad/M08-reporte-validacion-local-035.md"

echo "== Migration max 035 / no 036 =="
highest=$(ls "$MIG" | grep -E '^[0-9]{3}_' | sed 's/_.*//' | sort | tail -n1)
echo "Highest migration: $highest"
if [[ "$highest" != "035" ]]; then
  echo "Expected highest 035, got $highest"
  FAIL=1
fi
if ls "$MIG"/036_* >/dev/null 2>&1; then
  echo "FORBIDDEN: supabase/migrations/036_* exists"
  FAIL=1
fi
count=$(ls "$MIG"/*.sql 2>/dev/null | wc -l | tr -d ' ')
if [[ "$count" != "35" ]]; then
  echo "Expected 35 migration sql files, got $count"
  FAIL=1
else
  echo "OK migration count=35"
fi

echo "== 001–034 untouched (content hash vs git if available) =="
if git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  dirty_old=$(git -C "$ROOT" status --porcelain -- supabase/migrations/001_*.sql supabase/migrations/0[0-2]*.sql supabase/migrations/03[0-4]_*.sql 2>/dev/null || true)
  # Also check any modified under 001-034 via path list
  mod=$(git -C "$ROOT" diff --name-only -- supabase/migrations/ | grep -E 'migrations/0(0[1-9]|[12][0-9]|3[0-4])_' || true)
  staged=$(git -C "$ROOT" diff --cached --name-only -- supabase/migrations/ | grep -E 'migrations/0(0[1-9]|[12][0-9]|3[0-4])_' || true)
  if [[ -n "$mod" || -n "$staged" ]]; then
    echo "FORBIDDEN: modifications to migrations 001–034"
    echo "$mod"
    echo "$staged"
    FAIL=1
  else
    echo "OK 001–034 not modified in working tree"
  fi
else
  echo "Not a git repo; skip 001–034 dirty check"
fi

echo "== Frozen markers in 035 =="
for needle in \
  pet_responsibilities \
  pet_authorizations \
  pet_transfers \
  pet_status_history \
  m08_create_pet_with_principal \
  m08_accept_pet_transfer \
  m08_expire_pet_transfers \
  m08_mark_pet_deceased \
  m08_archive_pet \
  m08_restore_pet \
  pets_microchip_active_uniq \
  _m08_sync_owner_id_from_principal \
  PET_OWNER_ID_DIRECT_FORBIDDEN \
  BACKFILL_035 \
  enable row level security \
  revoke all on function \
  'pet.read' \
  m08.pet.created \
  STAGING NO AUTORIZADO
do
  if ! grep -qF "$needle" "$SQL035"; then
    echo "MISSING in 035: $needle"
    FAIL=1
  fi
done
echo "OK frozen markers"

echo "== No remote / linked ops in stage3b scripts =="
# Scan validation SQL only (this gate file intentionally names forbidden tokens).
if grep -Eiq 'db push|--linked|migration repair|supabase\.co|eyJ|service_role.*eyJ' \
  "$ROOT/scripts/sql/m08_validate_local_035.sql"; then
  echo "FORBIDDEN remote/linked/secret pattern in scripts/sql/m08_validate_local_035.sql"
  FAIL=1
else
  echo "OK no remote patterns in validation SQL"
fi

echo "== Legacy Android / UI not modified by this gate presence =="
require_file "$ROOT/app/src/main/java/com/comunidapp/app/data/repository/SupabaseRepositories.kt"
require_file "$ROOT/app/src/main/java/com/comunidapp/app/ui/screens/pets/MyPetsScreen.kt"
if git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  for f in \
    app/src/main/java/com/comunidapp/app/data/repository/SupabaseRepositories.kt \
    app/src/main/java/com/comunidapp/app/ui/screens/pets/MyPetsScreen.kt \
    app/src/main/java/com/comunidapp/app/ui/screens/pets/PetFormScreen.kt \
    app/src/main/java/com/comunidapp/app/ui/screens/pets/PetDetailScreen.kt
  do
    if [[ -f "$ROOT/$f" ]]; then
      # dirty if unstaged or staged diff exists
      if [[ -n "$(git -C "$ROOT" status --porcelain -- "$f")" ]]; then
        echo "FORBIDDEN: $f modified"
        FAIL=1
      fi
    fi
  done
  echo "OK legacy pets UI/repo clean in working tree"
fi

echo "== Docs mention staging block / Etapa 4 =="
for f in \
  "$ROOT/docs/02-arquitectura/M08-etapa-3b-migracion-035-local.md" \
  "$ROOT/docs/04-calidad/M08-reporte-validacion-local-035.md"
do
  grep -qi 'STAGING NO AUTORIZADO' "$f" || { echo "missing STAGING NO AUTORIZADO in $f"; FAIL=1; }
  grep -qi 'Etapa 4' "$f" || { echo "missing Etapa 4 in $f"; FAIL=1; }
done

if [[ "$FAIL" -ne 0 ]]; then
  echo "M08 STAGE 3B QUALITY CHECKS FAILED"
  exit 1
fi
echo "M08 STAGE 3B QUALITY CHECKS PASSED"
exit 0
