#!/usr/bin/env bash
# LeoVer M08 Etapa 3C — quality gate: migración 036 local (+ docs/scripts).
# No remotes. No --linked. No secrets.
set -euo pipefail
ROOT="${ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
FAIL=0
MIG="$ROOT/supabase/migrations"
SQL036="$MIG/036_m08_pet_repository_compatibility_rpcs.sql"

echo "== M08 Stage 3C migration 036 quality =="

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

require_file "$SQL036"
require_file "$ROOT/scripts/sql/m08_validate_local_036.sql"
require_file "$ROOT/docs/02-arquitectura/M08-etapa-3c-forward-fix-036-local.md"
require_file "$ROOT/docs/04-calidad/M08-plan-validacion-migracion-036.md"
require_file "$ROOT/docs/04-calidad/M08-reporte-validacion-local-036.md"

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
count=$(ls "$MIG"/*.sql 2>/dev/null | wc -l | tr -d ' ')
if [[ "$count" != "36" ]]; then
  echo "Expected 36 migration sql files, got $count"
  FAIL=1
else
  echo "OK migration count=36"
fi

echo "== 001–035 not modified in working tree =="
if git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  mod=$(git -C "$ROOT" diff --name-only -- supabase/migrations/ | grep -E 'migrations/0(0[1-9]|[12][0-9]|3[0-5])_' || true)
  staged=$(git -C "$ROOT" diff --cached --name-only -- supabase/migrations/ | grep -E 'migrations/0(0[1-9]|[12][0-9]|3[0-5])_' || true)
  if [[ -n "$mod" || -n "$staged" ]]; then
    echo "FORBIDDEN: modifications to migrations 001–035"
    echo "$mod"
    echo "$staged"
    FAIL=1
  else
    echo "OK 001–035 not modified in working tree"
  fi
else
  echo "Not a git repo; skip 001–035 dirty check"
fi

echo "== Four RPCs + privacy markers in 036 =="
for needle in \
  m08_update_pet_profile \
  m08_update_pet_health \
  m08_get_pet_access_context \
  m08_list_accessible_pets \
  pet.update \
  pet.manage_health \
  PET_MICROCHIP_ACTIVE_CONFLICT \
  'set search_path = public' \
  revoke all on function \
  STAGING NO AUTORIZADO
do
  if ! grep -qF "$needle" "$SQL036"; then
    echo "MISSING in 036: $needle"
    FAIL=1
  fi
done

if grep -qF 'm08_list_public_profile_pets' "$SQL036"; then
  # Allowed only as absence assertion / comment forbidding it
  if grep -Eiq 'create[[:space:]]+(or[[:space:]]+replace[[:space:]]+)?function[[:space:]]+public\.m08_list_public_profile_pets' "$SQL036"; then
    echo "FORBIDDEN: m08_list_public_profile_pets created in 036"
    FAIL=1
  fi
fi

echo "== Profile RPC must not SET owner_id/status/avatar =="
# Extract function body roughly between create ... m08_update_pet_profile and next create
prof_body=$(awk '/create or replace function public.m08_update_pet_profile/,/create or replace function public.m08_update_pet_health/' "$SQL036")
for bad in 'owner_id =' 'status =' 'avatar_file_asset_id =' 'deceased_at =' 'archived_at =' 'photo_url ='; do
  if echo "$prof_body" | grep -qiE "[[:space:]]$bad"; then
    echo "FORBIDDEN in profile SET: $bad"
    FAIL=1
  fi
done
# health fields must not appear in profile update SET (allow comments)
if echo "$prof_body" | grep -qiE 'vaccinations[[:space:]]*=' ; then
  echo "FORBIDDEN: profile RPC sets vaccinations"
  FAIL=1
fi
if echo "$prof_body" | grep -qiE 'health_notes[[:space:]]*=' ; then
  echo "FORBIDDEN: profile RPC sets health_notes"
  FAIL=1
fi
echo "OK profile SET constraints"

echo "== Health RPC must not modify profile identity =="
health_body=$(awk '/create or replace function public.m08_update_pet_health/,/create or replace function public.m08_get_pet_access_context/' "$SQL036")
for bad in 'name =' 'species =' 'breed =' 'microchip_id =' 'owner_id =' 'status =' 'avatar_file_asset_id ='; do
  if echo "$health_body" | grep -qiE "[[:space:]]$bad"; then
    echo "FORBIDDEN in health SET: $bad"
    FAIL=1
  fi
done
echo "OK health SET constraints"

echo "== Grants: PUBLIC/anon revoked; authenticated granted =="
for fn in m08_update_pet_profile m08_update_pet_health m08_get_pet_access_context m08_list_accessible_pets; do
  if ! grep -q "grant execute on function public.$fn" "$SQL036"; then
    echo "MISSING grant authenticated pattern for $fn"
    FAIL=1
  fi
done
if ! grep -qi 'revoke.*from public' "$SQL036"; then
  echo "MISSING revoke from public"
  FAIL=1
fi
if ! grep -qi 'revoke execute.*from anon' "$SQL036"; then
  echo "MISSING revoke from anon"
  FAIL=1
fi
echo "OK grant/revoke markers"

echo "== No remote / linked ops in stage3c validation SQL =="
# Scan validation SQL only (this gate file intentionally names forbidden tokens).
if grep -Eiq 'db push|--linked|migration repair|supabase\.co|eyJ|service_role.*eyJ' \
  "$ROOT/scripts/sql/m08_validate_local_036.sql"; then
  echo "FORBIDDEN remote/linked/secret pattern in scripts/sql/m08_validate_local_036.sql"
  FAIL=1
else
  echo "OK no remote patterns in validation SQL"
fi

echo "== Android / UI / repositories not modified =="
if git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  for f in \
    app/src/main/java/com/comunidapp/app/data/repository/SupabaseRepositories.kt \
    app/src/main/java/com/comunidapp/app/data/remote/supabase/SupabaseMappers.kt \
    app/src/main/java/com/comunidapp/app/ui/screens/pets/MyPetsScreen.kt \
    app/src/main/java/com/comunidapp/app/ui/screens/pets/PetFormScreen.kt \
    app/src/main/java/com/comunidapp/app/ui/screens/pets/PetDetailScreen.kt
  do
    if [[ -f "$ROOT/$f" ]]; then
      if [[ -n "$(git -C "$ROOT" status --porcelain -- "$f")" ]]; then
        echo "FORBIDDEN: $f modified"
        FAIL=1
      fi
    fi
  done
  # Also forbid PetRepository interface dirty
  for f in $(git -C "$ROOT" ls-files '**/PetRepository*' 2>/dev/null || true); do
    if [[ -n "$(git -C "$ROOT" status --porcelain -- "$f")" ]]; then
      echo "FORBIDDEN: $f modified"
      FAIL=1
    fi
  done
  echo "OK legacy Android clean in working tree"
fi

echo "== Docs staging block / Etapa 4B =="
for f in \
  "$ROOT/docs/02-arquitectura/M08-etapa-3c-forward-fix-036-local.md" \
  "$ROOT/docs/04-calidad/M08-reporte-validacion-local-036.md"
do
  grep -qi 'STAGING NO AUTORIZADO' "$f" || { echo "missing STAGING NO AUTORIZADO in $f"; FAIL=1; }
  grep -qi 'Etapa 4B' "$f" || { echo "missing Etapa 4B in $f"; FAIL=1; }
  grep -qi 'FORWARD-FIX 036 VALIDADO LOCALMENTE\|VALIDADO LOCALMENTE' "$f" || { echo "missing local validated status in $f"; FAIL=1; }
done

if [[ "$FAIL" -ne 0 ]]; then
  echo "M08 STAGE 3C QUALITY CHECKS FAILED"
  exit 1
fi
echo "M08 STAGE 3C QUALITY CHECKS PASSED"
exit 0
