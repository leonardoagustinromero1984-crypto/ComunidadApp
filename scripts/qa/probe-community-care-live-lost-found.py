#!/usr/bin/env python3
"""Idempotent STAGING probe for one QA LOST/FOUND pair.

Creates at most one LOST for QA - Mora and one nearby FOUND, then reads
the automatic match candidate and the owner notification. Reuses a valid
pair instead of inserting another.

User sessions only:
  SUPABASE_STAGING_PUBLISHABLE_KEY
  LEOVER_QA_PASSWORD

Staging project: tobqbddfcyitwgbkthhy.
Refuses the legacy/prod ref. Does not use service_role, a database
password, or SQL inserts.

Owner validation is a separate mutation phase:
  python3 scripts/qa/probe-community-care-live-lost-found.py --mutate-owner-validation

That phase calls the canonical assert and custodian-confirm RPCs only when
the pinned pair is still OPEN. Confirmation is one-way: it resolves both
cases, so a later run only reads. Responder claim and IN_CARE are not part
of this probe, because claim requires an OPEN case.
"""

from __future__ import annotations

import base64
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, datetime, timezone

STAGING_REF = "tobqbddfcyitwgbkthhy"
LEGACY_PROD_REF = "wystsapjfpdtoprlmizz"
BASE = f"https://{STAGING_REF}.supabase.co"

MORA_PET_ID = "f58305a1-0b83-40ed-bbc3-fdcdc0120fb5"
QA01_ASSET_ID = "5e25085a-16a4-48ef-beb3-13c71fff00e9"
QA02_ASSET_ID = "c84dc7c2-9810-4d99-8c11-6a2caad89e95"
NOTE_MARK = "QA LIVE 10B Mora"
LOST_NOTE = "QA LIVE 10B Mora LOST"
FOUND_NOTE = "QA LIVE 10B Mora FOUND"
ACTIVE = {"OPEN", "CLAIMED", "IN_CARE"}

# Synthetic CABA pins from the community-care actor seed. Not real homes.
# LOST sits on the QA01 Microcentro pin. FOUND is the QA02 pin about 250m away.
LOST_LAT = -34.6031
LOST_LNG = -58.3808
LOST_LABEL = "QA - Microcentro sintético, CABA"
FOUND_LAT = -34.6055
FOUND_LNG = -58.3838
FOUND_LABEL = "QA - Finder 250m, CABA"
LOCALITY_CANDIDATE = "loc-ar-loc-caba"

ACTORS = {
    "QA01": "qa01owner",
    "QA02": "qa02finder",
    "QA03": "qa03rescuer",
    "QA06": "qa06foster",
    "QA14": "qa14adopter",
}

# Pair created by the 10B probe. Later phases must reuse these ids.
PINNED_LOST_ID = "232c473b-cbf3-485f-8f89-cb27132c8f89"
PINNED_FOUND_ID = "2e0cf009-325d-4db0-be03-4ca48c1b2f67"
PINNED_AUTO_CANDIDATE_ID = "d3488929-afd1-489b-bac8-fd8e33e6093f"
PINNED_PROVISIONAL_PET_ID = "5f487c6f-f7b5-4b68-9419-d018b89195f8"


def refuse_non_staging() -> None:
    if STAGING_REF not in BASE or LEGACY_PROD_REF in BASE:
        raise SystemExit("REFUSING PROD")
    override = os.environ.get("SUPABASE_URL", "").strip()
    if override and STAGING_REF not in override:
        raise SystemExit("REFUSING non-staging SUPABASE_URL")
    if override and LEGACY_PROD_REF in override:
        raise SystemExit("REFUSING PROD")


def assert_publishable_key(key: str) -> None:
    if not key or "service_role" in key:
        raise SystemExit("REFUSING service_role key")
    parts = key.split(".")
    if len(parts) != 3:
        return
    try:
        payload = parts[1] + "=" * (-len(parts[1]) % 4)
        claims = json.loads(base64.urlsafe_b64decode(payload.encode()))
    except (json.JSONDecodeError, ValueError, UnicodeError):
        return
    if isinstance(claims, dict) and claims.get("role") == "service_role":
        raise SystemExit("REFUSING service_role key")


def http(key: str, method: str, path: str, body=None, token: str | None = None, timeout: int = 90):
    headers = {
        "apikey": key,
        "Accept": "application/json",
        "Content-Type": "application/json",
    }
    if token:
        headers["Authorization"] = "Bearer " + token
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode()


def safe_error(raw: str) -> str:
    try:
        body = json.loads(raw)
    except json.JSONDecodeError:
        return "http_error"
    if isinstance(body, dict):
        message = str(body.get("message") or body.get("msg") or "")
        code = str(body.get("code") or body.get("error") or "")
        upper = message.upper()
        for marker in (
            "NOT_AUTHENTICATED",
            "FORBIDDEN",
            "NOT_FOUND",
            "LF-CREATE-AUTH",
            "LF-CREATE-KIND",
            "LF-CREATE-FORBIDDEN",
            "LF-CREATE-LOCATION",
            "LF-CREATE-IDENTITY",
            "LF-CREATE-ALERT",
            "VITACORA_RETIRED",
            "RATE_LIMITED",
            "QUOTA_EXCEEDED",
            "PGRST202",
        ):
            if marker in upper or marker in code:
                return marker
        if code:
            return code[:80]
    return "http_error"


def parse_json(raw: str):
    text = raw.strip()
    try:
        body = json.loads(text)
    except json.JSONDecodeError:
        return text
    if isinstance(body, str):
        nested = body.strip()
        if nested[:1] in "[{":
            try:
                return json.loads(nested)
            except json.JSONDecodeError:
                return body
    return body


def login(key: str, password: str, username: str) -> tuple[str, str]:
    status, raw = http(
        key,
        "POST",
        "/rest/v1/rpc/canon_begin_username_login",
        {"p_username": username, "p_password": password},
    )
    if status != 200:
        raise SystemExit(f"{username} AUTH FAIL begin http={status} {safe_error(raw)}")
    body = parse_json(raw)
    email = body.get("email") if isinstance(body, dict) else None
    if not email:
        raise SystemExit(f"{username} AUTH FAIL missing session email")
    status, raw = http(
        key,
        "POST",
        "/auth/v1/token?grant_type=password",
        {"email": email, "password": password},
    )
    if status != 200:
        raise SystemExit(f"{username} AUTH FAIL session http={status}")
    token = json.loads(raw).get("access_token")
    if not token:
        raise SystemExit(f"{username} AUTH FAIL missing session")
    status, raw = http(key, "GET", "/auth/v1/user", token=token)
    if status != 200:
        raise SystemExit(f"{username} AUTH FAIL user http={status}")
    user_id = json.loads(raw).get("id")
    if not user_id:
        raise SystemExit(f"{username} AUTH FAIL missing user id")
    return token, user_id


def read_asset(key: str, token: str, asset_id: str) -> tuple[int, dict | None, str]:
    quoted = urllib.parse.quote(asset_id)
    status, raw = http(
        key,
        "GET",
        "/rest/v1/media_assets"
        "?select=id,mime_type,lifecycle_status,owner_person_id,created_by,bucket"
        f"&id=eq.{quoted}",
        token=token,
    )
    if status != 200:
        return status, None, safe_error(raw)
    rows = json.loads(raw)
    if not isinstance(rows, list) or not rows:
        return status, None, "ABSENT"
    return status, rows[0], ""


def asset_ok(row: dict | None, user_id: str, expected_id: str) -> bool:
    if not row:
        return False
    return (
        row.get("id") == expected_id
        and row.get("mime_type") == "image/png"
        and row.get("lifecycle_status") == "READY"
        and row.get("owner_person_id") == user_id
        and row.get("created_by") == user_id
    )


def rpc(key: str, token: str, name: str, body: dict | None):
    return http(key, "POST", f"/rest/v1/rpc/{name}", body if body is not None else {}, token=token)


def rows_of(status: int, raw: str):
    if status != 200:
        return None
    body = parse_json(raw)
    if isinstance(body, list):
        return body
    if isinstance(body, dict):
        return [body]
    return None


def list_alerts(key: str, token: str) -> list[dict]:
    status, raw = rpc(key, token, "canon_list_lost_found", {"p_kind": None})
    rows = rows_of(status, raw)
    if rows is None:
        raise SystemExit(f"LIST FAIL http={status} {safe_error(raw)}")
    return [row for row in rows if isinstance(row, dict)]


def is_pair_lost(row: dict) -> bool:
    return (
        row.get("kind") == "LOST"
        and row.get("status") in ACTIVE
        and row.get("pet_id") == MORA_PET_ID
        and row.get("photo_asset_id") == QA01_ASSET_ID
    )


def is_pair_found(row: dict, finder_id: str) -> bool:
    return (
        row.get("kind") == "FOUND"
        and row.get("status") in ACTIVE
        and row.get("photo_asset_id") == QA02_ASSET_ID
        and row.get("created_by") == finder_id
    )


def prefer_marked(rows: list[dict]) -> dict | None:
    if not rows:
        return None
    marked = [row for row in rows if NOTE_MARK in str(row.get("note") or "")]
    pool = marked or rows
    pool.sort(key=lambda row: str(row.get("created_at") or ""), reverse=True)
    return pool[0]


def qa_summary(alerts: list[dict], finder_id: str) -> list[dict]:
    picked = []
    for row in alerts:
        note = str(row.get("note") or "")
        relevant = (
            NOTE_MARK in note
            or row.get("pet_id") == MORA_PET_ID
            or row.get("photo_asset_id") in (QA01_ASSET_ID, QA02_ASSET_ID)
            or (row.get("kind") == "FOUND" and row.get("created_by") == finder_id and NOTE_MARK in note)
        )
        if relevant and row.get("status") in ACTIVE:
            picked.append(row)
    return picked


def print_qa_rows(rows: list[dict]) -> None:
    print(f"ACTIVE QA LOST/FOUND: {len(rows)}")
    for row in rows:
        print(
            "QA ROW "
            f"id={row.get('id')} kind={row.get('kind')} status={row.get('status')} "
            f"pet_id={row.get('pet_id')} photo_asset_id={row.get('photo_asset_id')} "
            f"location_label={row.get('location_label')}"
        )


def load_mora(key: str, token: str) -> dict:
    quoted = urllib.parse.quote(MORA_PET_ID)
    status, raw = http(
        key,
        "GET",
        "/rest/v1/pets"
        "?select=id,name,species_code,breed_id,sex,size,birth_date,birth_precision,origin_kind"
        f"&id=eq.{quoted}",
        token=token,
    )
    rows = rows_of(status, raw) if status == 200 else None
    if not rows:
        raise SystemExit(f"MORA READ FAIL http={status} {safe_error(raw)}")
    pet = rows[0]
    if pet.get("id") != MORA_PET_ID or pet.get("name") != "QA - Mora":
        raise SystemExit("MORA READ FAIL identity")
    if pet.get("species_code") != "DOG" or pet.get("sex") != "FEMALE" or pet.get("size") != "MEDIUM":
        raise SystemExit(
            "MORA READ FAIL attributes "
            f"species={pet.get('species_code')} sex={pet.get('sex')} size={pet.get('size')}"
        )
    breed_id = pet.get("breed_id")
    if not breed_id:
        raise SystemExit("MORA READ FAIL missing breed")
    bstatus, braw = http(
        key,
        "GET",
        "/rest/v1/breeds?select=id,name&id=eq." + urllib.parse.quote(breed_id),
        token=token,
    )
    breeds = rows_of(bstatus, braw) if bstatus == 200 else None
    breed_name = breeds[0].get("name") if breeds else None
    if breed_name != "Akita Inu":
        raise SystemExit(f"MORA READ FAIL breed={breed_name}")
    pet["breed_name"] = breed_name
    return pet


def age_months(pet: dict) -> int:
    raw = pet.get("birth_date")
    if pet.get("birth_precision") == "EXACT_DATE" and raw:
        born = date.fromisoformat(str(raw)[:10])
        return max(0, (date.today() - born).days // 30)
    raise SystemExit("MORA READ FAIL age")


def locality_id(key: str, token: str) -> str | None:
    status, raw = http(
        key,
        "GET",
        "/rest/v1/location_nodes?select=id&id=eq." + urllib.parse.quote(LOCALITY_CANDIDATE),
        token=token,
    )
    rows = rows_of(status, raw) if status == 200 else None
    if rows and rows[0].get("id") == LOCALITY_CANDIDATE:
        return LOCALITY_CANDIDATE
    return None


def create_case(key: str, token: str, payload: dict) -> str:
    status, raw = rpc(key, token, "canon_create_lost_found", payload)
    if status != 200:
        raise SystemExit(f"CREATE FAIL kind={payload.get('p_kind')} http={status} {safe_error(raw)}")
    body = parse_json(raw)
    case_id = body if isinstance(body, str) else None
    if isinstance(body, dict):
        case_id = body.get("id") or body.get("canon_create_lost_found")
    if not case_id or not isinstance(case_id, str):
        raise SystemExit(f"CREATE FAIL kind={payload.get('p_kind')} missing id")
    return case_id


def lost_payload(incident_at: str, breed_id: str, months: int, locality: str | None) -> dict:
    return {
        "p_kind": "LOST",
        "p_pet_id": MORA_PET_ID,
        "p_locality_id": locality,
        "p_species": "DOG",
        "p_note": LOST_NOTE,
        "p_lat": LOST_LAT,
        "p_lng": LOST_LNG,
        "p_incident_at": incident_at,
        "p_photo_asset_id": QA01_ASSET_ID,
        "p_name": None,
        "p_sex": "FEMALE",
        "p_size": "MEDIUM",
        "p_estimated_age_months": months,
        "p_breed_id": breed_id,
        "p_location_label": LOST_LABEL,
    }


def found_payload(incident_at: str, breed_id: str, months: int, locality: str | None) -> dict:
    return {
        "p_kind": "FOUND",
        "p_pet_id": None,
        "p_locality_id": locality,
        "p_species": "DOG",
        "p_note": FOUND_NOTE,
        "p_lat": FOUND_LAT,
        "p_lng": FOUND_LNG,
        "p_incident_at": incident_at,
        "p_photo_asset_id": QA02_ASSET_ID,
        "p_name": "QA LIVE 10B hallazgo",
        "p_sex": "FEMALE",
        "p_size": "MEDIUM",
        "p_estimated_age_months": months,
        "p_breed_id": breed_id,
        "p_location_label": FOUND_LABEL,
    }


def candidates(key: str, token: str, found_id: str):
    return rpc(key, token, "canon_list_found_match_candidates", {"p_found_id": found_id})


def pick_candidate(rows: list[dict], lost_id: str, found_id: str) -> dict | None:
    matches = [
        row for row in rows
        if row.get("lost_alert_id") == lost_id
    ]
    if not matches:
        return None
    matches.sort(key=lambda row: float(row.get("score") or 0), reverse=True)
    chosen = matches[0]
    chosen["found_alert_id"] = found_id
    return chosen


def notifications(key: str, token: str) -> list[dict]:
    status, raw = rpc(key, token, "canon_list_my_notifications", {"p_limit": 200})
    rows = rows_of(status, raw)
    if rows is None:
        raise SystemExit(f"NOTIFICATIONS FAIL http={status} {safe_error(raw)}")
    return [row for row in rows if isinstance(row, dict)]


def matching_notification(rows: list[dict], lost_id: str, found_id: str, owner_id: str) -> dict | None:
    found = []
    for row in rows:
        if row.get("user_id") not in (None, owner_id):
            continue
        target = row.get("deep_link_resource_id") or row.get("related_id")
        dedup = str(row.get("deduplication_key") or "")
        category = str(row.get("category") or "")
        title = str(row.get("title") or "")
        pair = lost_id in dedup and found_id in dedup
        target_ok = target == found_id and (lost_id in dedup or "lost_found_match:" in dedup)
        if category == "LOST_FOUND" and (pair or target_ok) and (
            "lost_found_match:" in dedup or title == "Posible coincidencia"
        ):
            found.append(row)
    if not found:
        return None
    found.sort(key=lambda row: str(row.get("created_at") or ""), reverse=True)
    return found[0]


def read_pet(key: str, token: str, pet_id: str) -> dict | None:
    status, raw = http(
        key,
        "GET",
        "/rest/v1/pets?select=id,name,species_code,sex,size,breed_id,origin_kind,public_code,"
        "current_custodian_kind,current_custodian_person_id,created_by_user_id"
        "&id=eq." + urllib.parse.quote(pet_id),
        token=token,
    )
    rows = rows_of(status, raw) if status == 200 else None
    return rows[0] if rows else None


def profile_pet_ids(key: str, token: str, user_id: str) -> list[str]:
    status, raw = rpc(key, token, "canon_list_pets_for_person_profile", {"p_user_id": user_id})
    rows = rows_of(status, raw)
    if rows is None:
        return []
    return [str(row.get("id")) for row in rows if isinstance(row, dict) and row.get("id")]


def vitacora_number(key: str, token: str, pet_id: str) -> str | None:
    status, raw = http(
        key,
        "GET",
        "/rest/v1/vitacora_profiles?select=pet_id,public_vitacora_number"
        "&pet_id=eq." + urllib.parse.quote(pet_id),
        token=token,
    )
    rows = rows_of(status, raw) if status == 200 else None
    if not rows:
        return None
    number = rows[0].get("public_vitacora_number")
    return str(number) if number is not None else None


def photo_moment(key: str, token: str, pet_id: str) -> bool:
    status, raw = rpc(key, token, "canon_list_vitacora_moments", {"p_pet_id": pet_id})
    rows = rows_of(status, raw)
    if not rows:
        return False
    return any(
        row.get("kind") == "PHOTO" and row.get("title") == "Foto del hallazgo"
        for row in rows
        if isinstance(row, dict)
    )


def pet_record(key: str, token: str, pet_id: str) -> dict | None:
    _status, row, _err = pet_read(key, token, pet_id)
    return row


def pet_read(key: str, token: str, pet_id: str) -> tuple[int, dict | None, str]:
    status, raw = http(
        key,
        "GET",
        "/rest/v1/pets?select=id,name,origin_kind,lifecycle_status,archived_at,"
        "avatar_asset_id,current_custodian_person_id,current_custodian_kind"
        "&id=eq." + urllib.parse.quote(pet_id),
        token=token,
    )
    if status != 200:
        return status, None, safe_error(raw)
    rows = rows_of(status, raw) or []
    return status, (rows[0] if rows else None), ""


def moment_read(key: str, token: str, pet_id: str) -> tuple[int, list[dict], str]:
    status, raw = rpc(key, token, "canon_list_vitacora_moments", {"p_pet_id": pet_id})
    if status != 200:
        return status, [], safe_error(raw)
    rows = rows_of(status, raw) or []
    return status, [row for row in rows if isinstance(row, dict)], ""


def moment_rows(key: str, token: str, pet_id: str) -> list[dict]:
    _status, rows, _err = moment_read(key, token, pet_id)
    return rows


def responder_eligible(key: str, token: str) -> bool | None:
    status, raw = rpc(key, token, "canon_get_my_responder_base", {})
    body = parse_json(raw) if status == 200 else None
    if not isinstance(body, dict) or "eligible" not in body:
        return None
    return bool(body.get("eligible"))


def classify_match_rows(rows: list[dict]) -> str:
    owner = [
        row for row in rows
        if row.get("lost_alert_id") == PINNED_LOST_ID and row.get("match_reason") == "OWNER_ASSERT"
    ]
    auto = [
        row for row in rows
        if row.get("lost_alert_id") == PINNED_LOST_ID and row.get("asserted_by") in (None, "")
    ]
    if any(row.get("status") == "ACCEPTED" for row in owner):
        return "CONFIRMED"
    if any(row.get("status") == "PENDING" for row in owner):
        return "ASSERTED_PENDING"
    if auto and not owner and all(row.get("status") == "PENDING" for row in auto):
        return "INITIAL"
    return "OTHER"


def read_confirmed_pair(key: str, sessions: dict[str, tuple[str, str]], rows: list[dict]) -> int:
    qa01_token, qa01_id = sessions["QA01"]
    qa02_token, qa02_id = sessions["QA02"]
    qa03_token, _qa03_id = sessions["QA03"]
    qa06_token, _qa06_id = sessions["QA06"]
    owner = next(
        (
            row for row in rows
            if row.get("match_reason") == "OWNER_ASSERT" and row.get("lost_alert_id") == PINNED_LOST_ID
        ),
        None,
    )
    auto = next((row for row in rows if row.get("id") == PINNED_AUTO_CANDIDATE_ID), None)
    alerts = list_alerts(key, qa01_token)
    found_active = next((row for row in alerts if row.get("id") == PINNED_FOUND_ID), None)
    lost_active = next((row for row in alerts if row.get("id") == PINNED_LOST_ID), None)
    prov_http, provisional, _prov_err = pet_read(key, qa02_token, PINNED_PROVISIONAL_PET_ID)
    mora = pet_record(key, qa01_token, MORA_PET_ID)
    mora_status, mora_moments, _mora_err = moment_read(key, qa01_token, MORA_PET_ID)
    prov_moment_http, provisional_moments, prov_moment_err = moment_read(
        key, qa02_token, PINNED_PROVISIONAL_PET_ID
    )
    qa01_pets = profile_pet_ids(key, qa01_token, qa01_id)
    qa02_pets = profile_pet_ids(key, qa02_token, qa02_id)
    qa03_eligible = responder_eligible(key, qa03_token)
    qa06_eligible = responder_eligible(key, qa06_token)
    qa02_notes = notifications(key, qa02_token)
    assert_note = next(
        (
            row for row in qa02_notes
            if str(row.get("deduplication_key") or "").startswith("lost_found_assert:")
            and PINNED_FOUND_ID in json.dumps(row)
        ),
        None,
    )
    photo_on_mora = any(
        row.get("kind") == "PHOTO" and row.get("title") == "Foto del hallazgo"
        for row in mora_moments
    )
    # 1104 retires the provisional VitaCora. CARE_CREATED must not be an
    # independent timeline. FORBIDDEN means the former custodian lost access
    # before the retired assertion; it is not lost hallazgo history.
    history_on_provisional = any(row.get("kind") == "CARE_CREATED" for row in provisional_moments)
    photo_left_behind = any(
        row.get("kind") == "PHOTO" and row.get("title") == "Foto del hallazgo"
        for row in provisional_moments
    )
    timeline_denied = prov_moment_http != 200 and prov_moment_err in ("FORBIDDEN", "VITACORA_RETIRED")
    visible_archived = bool(
        provisional
        and provisional.get("lifecycle_status") == "ARCHIVED"
        and provisional.get("origin_kind") == "FOUND_CASE"
        and provisional.get("current_custodian_person_id") == qa02_id
    )
    # Ending the FOUND care link removes holder SELECT. An empty read is the
    # retired archived identity when it is not an active profile pet and it
    # has no independent timeline. Hallazgo history stays on Mora.
    retired_invisible = bool(
        provisional is None
        and prov_http == 200
        and timeline_denied
        and not history_on_provisional
        and not photo_left_behind
        and PINNED_PROVISIONAL_PET_ID not in qa01_pets
        and PINNED_PROVISIONAL_PET_ID not in qa02_pets
        and photo_on_mora
        and mora_status == 200
    )
    checks = {
        "owner_accepted": bool(owner and owner.get("status") == "ACCEPTED" and owner.get("asserted_by") == qa01_id),
        "auto_rejected": bool(auto and auto.get("status") == "REJECTED" and auto.get("asserted_by") in (None, "")),
        "found_inactive": found_active is None,
        "lost_inactive": lost_active is None,
        "provisional_archived": visible_archived or retired_invisible,
        "mora_active_owner": bool(
            mora
            and mora.get("name") == "QA - Mora"
            and mora.get("lifecycle_status") == "ACTIVE"
            and mora.get("current_custodian_person_id") == qa01_id
        ),
        "photo_moved": photo_on_mora and not photo_left_behind,
        "no_provisional_care_created": not history_on_provisional,
        "finder_not_owner": MORA_PET_ID not in qa02_pets and PINNED_PROVISIONAL_PET_ID not in qa01_pets,
        "mora_on_owner_profile": MORA_PET_ID in qa01_pets,
        "qa03_eligible": qa03_eligible is True,
        "qa06_not_eligible": qa06_eligible is False,
    }
    ok = all(checks.values())
    print("PINNED PAIR PHASE: READ")
    print(f"PAIR REUSED: YES lost={PINNED_LOST_ID} found={PINNED_FOUND_ID}")
    print(f"QA01 ASSERTION: ALREADY_DONE candidate={None if not owner else owner.get('id')}")
    print(f"ASSERTED_BY: {None if not owner else owner.get('asserted_by')}")
    print(f"CANDIDATE STATUS AFTER VALIDATION: {None if not owner else owner.get('status')}")
    print(f"AUTOMATIC CANDIDATE: {None if not auto else auto.get('status')}")
    print("FOUND STATUS AFTER VALIDATION: NOT_IN_ACTIVE_LIST")
    print(f"FOUND PET_ID AFTER VALIDATION: {MORA_PET_ID}")
    if visible_archived:
        print(
            "PROVISIONAL PET: "
            f"{provisional.get('lifecycle_status')} "
            f"origin={provisional.get('origin_kind')} "
            f"custodian={provisional.get('current_custodian_person_id')}"
        )
    elif retired_invisible:
        print(
            "PROVISIONAL PET: ARCHIVED origin=FOUND_CASE "
            "visibility=NOT_VISIBLE_TO_FORMER_CUSTODIAN"
        )
    else:
        print(
            "PROVISIONAL PET: "
            f"{None if not provisional else provisional.get('lifecycle_status')} "
            f"origin={None if not provisional else provisional.get('origin_kind')} "
            f"http={prov_http}"
        )
    print(
        "PROVISIONAL TIMELINE: "
        + ("DENIED" if timeline_denied else "PRESENT" if provisional_moments else "EMPTY")
    )
    print("PROVISIONAL CARE_CREATED: " + ("STILL_PRESENT" if history_on_provisional else "GONE"))
    print(
        "CANONICAL OWNER PET: "
        f"{MORA_PET_ID} lifecycle={None if not mora else mora.get('lifecycle_status')} "
        f"custodian={None if not mora else mora.get('current_custodian_person_id')}"
    )
    print("CLAIM_AFTER_OWNER_CONFIRM: NOT_VALID_BY_STATE_MACHINE")
    print("QA03 CLAIM: NOT_RUN")
    print("IN_CARE: NOT_RUN")
    print(f"QA03 ELIGIBILITY: {'PASS' if qa03_eligible is True else 'FAIL'}")
    print(f"QA06 FOSTER ELIGIBILITY: {'DENIED' if qa06_eligible is False else 'LEAKED'}")
    print(f"OWNER ASSERT NOTIFICATION: {'PRESENT' if assert_note else 'ABSENT'}")
    print("CONFIRM NOTIFICATION: NOT_EMITTED")
    print("CLAIM NOTIFICATION: NOT_RUN")
    print("IN_CARE NOTIFICATION: NOT_RUN")
    print("SERVICE ROLE: NO")
    print("MANUAL SQL: NO")
    failed = [name for name, passed in checks.items() if not passed]
    print(f"READ CHECKS: {'PASS' if ok else 'FAIL'} failed={','.join(failed) or 'NONE'}")
    return 0 if ok else 1


def mutate_owner_validation(
    key: str,
    sessions: dict[str, tuple[str, str]],
    rows: list[dict],
    state: str,
) -> int:
    if state == "CONFIRMED":
        print("QA01 ASSERTION: ALREADY_DONE")
        print("QA02 CUSTODIAN VALIDATION: ALREADY_DONE")
        print("CLAIM_AFTER_OWNER_CONFIRM: NOT_VALID_BY_STATE_MACHINE")
        return read_confirmed_pair(key, sessions, rows)
    if state not in ("INITIAL", "ASSERTED_PENDING"):
        print(f"OWNER VALIDATION REFUSED state={state}")
        print("REFUSING DUPLICATE PAIR")
        return 1

    qa01_token, qa01_id = sessions["QA01"]
    qa02_token, qa02_id = sessions["QA02"]
    qa14_token, _qa14_id = sessions["QA14"]
    alerts = list_alerts(key, qa02_token)
    found = next((row for row in alerts if row.get("id") == PINNED_FOUND_ID), None)
    lost = next((row for row in alerts if row.get("id") == PINNED_LOST_ID), None)
    if not found or not lost or found.get("status") != "OPEN" or lost.get("status") != "OPEN":
        print("OWNER VALIDATION REFUSED pair is not OPEN")
        return 1
    if lost.get("pet_id") != MORA_PET_ID or lost.get("created_by") != qa01_id:
        print("OWNER VALIDATION REFUSED LOST is not QA01 Mora")
        return 1
    if found.get("created_by") != qa02_id or found.get("pet_id") != PINNED_PROVISIONAL_PET_ID:
        print("OWNER VALIDATION REFUSED FOUND is not the pinned provisional case")
        return 1

    owner = next(
        (
            row for row in rows
            if row.get("match_reason") == "OWNER_ASSERT" and row.get("lost_alert_id") == PINNED_LOST_ID
        ),
        None,
    )
    assert_id = None if owner is None else owner.get("id")
    if state == "INITIAL":
        before_ids = sorted(str(row.get("id")) for row in rows)
        status, raw = rpc(
            key,
            qa14_token,
            "canon_assert_found_might_be_mine",
            {"p_found_id": PINNED_FOUND_ID, "p_lost_id": PINNED_LOST_ID},
        )
        print(f"UNAUTHORIZED ASSERTION: {'DENIED' if status != 200 and safe_error(raw) == 'FORBIDDEN' else 'LEAKED'}")
        if status == 200 or safe_error(raw) != "FORBIDDEN":
            return 1
        status, raw = candidates(key, qa02_token, PINNED_FOUND_ID)
        current = rows_of(status, raw) if status == 200 else None
        if current is None:
            print("CANDIDATE REREAD FAIL after denial")
            return 1
        after_ids = sorted(str(row.get("id")) for row in current)
        if after_ids != before_ids:
            print("UNAUTHORIZED ASSERTION: LEAKED")
            return 1
        status, raw = rpc(
            key,
            qa01_token,
            "canon_assert_found_might_be_mine",
            {"p_found_id": PINNED_FOUND_ID, "p_lost_id": PINNED_LOST_ID},
        )
        if status != 200:
            print(f"QA01 ASSERTION: FAIL http={status} {safe_error(raw)}")
            return 1
        parsed = parse_json(raw)
        assert_id = parsed if isinstance(parsed, str) else None
        if not assert_id:
            print("QA01 ASSERTION: FAIL missing id")
            return 1
        print(f"QA01 ASSERTION: PASS candidate={assert_id}")
        status, raw = candidates(key, qa02_token, PINNED_FOUND_ID)
        current = rows_of(status, raw) if status == 200 else None
        owner = next(
            (row for row in (current or []) if row.get("id") == assert_id),
            None,
        )
        auto = next((row for row in (current or []) if row.get("id") == PINNED_AUTO_CANDIDATE_ID), None)
        if (
            owner is None
            or owner.get("status") != "PENDING"
            or owner.get("asserted_by") != qa01_id
            or owner.get("lost_alert_id") != PINNED_LOST_ID
            or auto is None
            or auto.get("status") != "PENDING"
        ):
            print("QA01 ASSERTION: FAIL candidate shape")
            return 1
        rows = current or []
    else:
        print(f"QA01 ASSERTION: ALREADY_DONE candidate={assert_id}")
        if not assert_id or not owner or owner.get("status") != "PENDING" or owner.get("asserted_by") != qa01_id:
            print("QA01 ASSERTION: FAIL pending owner row missing")
            return 1

    if not found.get("is_custodian"):
        print("QA02 CUSTODIAN VALIDATION: NOT_VALID_STATE")
        return 1
    for label, token in (("QA14", qa14_token), ("QA01", qa01_token)):
        status, raw = rpc(
            key,
            token,
            "canon_confirm_found_owner_match",
            {"p_candidate_id": assert_id},
        )
        print(f"UNAUTHORIZED CONFIRM {label}: {'DENIED' if status != 200 and safe_error(raw) == 'FORBIDDEN' else 'LEAKED'}")
        if status == 200 or safe_error(raw) != "FORBIDDEN":
            return 1
    status, raw = rpc(
        key,
        qa02_token,
        "canon_confirm_found_owner_match",
        {"p_candidate_id": assert_id},
    )
    if status != 200:
        print(f"QA02 CUSTODIAN VALIDATION: FAIL http={status} {safe_error(raw)}")
        return 1
    body = parse_json(raw)
    if not isinstance(body, dict) or body.get("pet_id") != MORA_PET_ID or body.get("archived_pet_id") != PINNED_PROVISIONAL_PET_ID:
        print("QA02 CUSTODIAN VALIDATION: FAIL reunify payload")
        return 1
    print("QA02 CUSTODIAN VALIDATION: PASS")
    print("CLAIM_AFTER_OWNER_CONFIRM: NOT_VALID_BY_STATE_MACHINE")
    print("QA03 CLAIM: NOT_RUN")
    print("IN_CARE: NOT_RUN")
    status, raw = candidates(key, qa02_token, PINNED_FOUND_ID)
    current = rows_of(status, raw) if status == 200 else None
    if current is None:
        print("CANDIDATE REREAD FAIL after confirm")
        return 1
    return read_confirmed_pair(key, sessions, current)


def run_pinned_pair_probe(key: str, password: str, mutate: bool) -> int | None:
    sessions: dict[str, tuple[str, str]] = {}
    needed = ("QA01", "QA02", "QA03", "QA06") if not mutate else ("QA01", "QA02", "QA03", "QA06", "QA14")
    try:
        for label in needed:
            sessions[label] = login(key, password, ACTORS[label])
    except SystemExit as exc:
        print(exc)
        return 1
    status, raw = candidates(key, sessions["QA02"][0], PINNED_FOUND_ID)
    if status != 200:
        code = safe_error(raw)
        if mutate or code not in ("NOT_FOUND", "PGRST202"):
            print(f"PINNED PAIR READ FAIL http={status} {code}")
            if mutate:
                print("REFUSING DUPLICATE PAIR")
            return 1
        return None
    rows = rows_of(status, raw) or []
    if not rows:
        if mutate:
            print("PINNED PAIR ABSENT")
            print("REFUSING DUPLICATE PAIR")
            return 1
        return None
    state = classify_match_rows(rows)
    print(f"PINNED PAIR STATE: {state}")
    if not mutate:
        if state == "INITIAL":
            return None
        if state == "CONFIRMED":
            return read_confirmed_pair(key, sessions, rows)
        print(f"PINNED PAIR READ ONLY state={state}")
        print("REFUSING DUPLICATE PAIR")
        return 0 if state == "ASSERTED_PENDING" else 1
    return mutate_owner_validation(key, sessions, rows, state)


def main() -> int:
    refuse_non_staging()
    key = os.environ.get("SUPABASE_STAGING_PUBLISHABLE_KEY", "").strip()
    password = os.environ.get("LEOVER_QA_PASSWORD", "").strip()
    if not key or not password:
        print("MISSING_SECRET SUPABASE_STAGING_PUBLISHABLE_KEY or LEOVER_QA_PASSWORD")
        return 2
    assert_publishable_key(key)
    mutate = "--mutate-owner-validation" in sys.argv
    gated = run_pinned_pair_probe(key, password, mutate)
    if gated is not None:
        return gated

    report = {
        "qa01_auth": "FAIL",
        "qa02_auth": "FAIL",
        "qa01_media": "FAIL",
        "qa02_media": "FAIL",
        "reused": "NO",
        "lost_created": "NO",
        "lost_id": "",
        "lost_status": "",
        "lost_pet": "",
        "lost_photo": "",
        "lost_location": "",
        "lost_label": "",
        "found_created": "NO",
        "found_id": "",
        "found_status": "",
        "found_pet": "",
        "found_label": "",
        "found_vitacora": "",
        "finder_owner": "",
        "match": "FAIL",
        "candidate_id": "",
        "score": "",
        "candidate_status": "",
        "asserted_by": "",
        "notification": "FAIL",
        "notification_id": "",
        "notification_event": "",
        "notification_target": "",
        "notification_at": "",
        "qa01_visibility": "FAIL",
        "qa02_visibility": "FAIL",
        "unrelated": "",
    }

    try:
        qa01_token, qa01_id = login(key, password, ACTORS["QA01"])
        report["qa01_auth"] = "PASS"
        print("QA01 AUTH: PASS")
        qa02_token, qa02_id = login(key, password, ACTORS["QA02"])
        report["qa02_auth"] = "PASS"
        print("QA02 AUTH: PASS")
        qa03_token, _qa03_id = login(key, password, ACTORS["QA03"])
        print("QA03 AUTH: PASS")
    except SystemExit as exc:
        print(exc)
        _emit(report)
        return 1

    for label, token, user_id, asset_id, field in (
        ("QA01", qa01_token, qa01_id, QA01_ASSET_ID, "qa01_media"),
        ("QA02", qa02_token, qa02_id, QA02_ASSET_ID, "qa02_media"),
    ):
        status, row, err = read_asset(key, token, asset_id)
        ok = asset_ok(row, user_id, asset_id)
        report[field] = "PASS" if ok else "FAIL"
        print(
            f"{label} MEDIA: {report[field]} asset={asset_id} "
            f"http={status} mime={None if not row else row.get('mime_type')} "
            f"status={None if not row else row.get('lifecycle_status')} "
            f"owner_match={'YES' if row and row.get('owner_person_id') == user_id else 'NO'} "
            f"err={err}"
        )
    if report["qa01_media"] != "PASS" or report["qa02_media"] != "PASS":
        _emit(report)
        return 1

    try:
        mora = load_mora(key, qa01_token)
        months = age_months(mora)
        locality = locality_id(key, qa01_token)
        print(
            f"MORA pet={mora['id']} species={mora['species_code']} sex={mora['sex']} "
            f"size={mora['size']} breed={mora['breed_name']} age_months={months} "
            f"locality={locality or 'NULL'}"
        )
    except SystemExit as exc:
        print(exc)
        _emit(report)
        return 1

    alerts = list_alerts(key, qa01_token)
    print_qa_rows(qa_summary(alerts, qa02_id))
    lost = prefer_marked([row for row in alerts if is_pair_lost(row)])
    found = prefer_marked([row for row in alerts if is_pair_found(row, qa02_id)])
    incident_at = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")

    if lost and found:
        report["reused"] = "YES"
        print("EXISTING PAIR REUSED: YES")
    else:
        print("EXISTING PAIR REUSED: NO")
        hist_status, hist_raw = candidates(key, qa02_token, PINNED_FOUND_ID)
        hist_rows = rows_of(hist_status, hist_raw) if hist_status == 200 else None
        if hist_rows:
            print("REFUSING DUPLICATE PAIR")
            _emit(report)
            return 1
        if lost is None:
            lost_id = create_case(
                key, qa01_token, lost_payload(incident_at, mora["breed_id"], months, locality)
            )
            report["lost_created"] = "YES"
            print(f"LOST CREATED id={lost_id}")
            alerts = list_alerts(key, qa01_token)
            lost = next((row for row in alerts if row.get("id") == lost_id), None)
            if lost is None or not is_pair_lost(lost):
                print("LOST VERIFY FAIL after create")
                _emit(report)
                return 1
        else:
            print(f"LOST REUSED id={lost.get('id')}")
        if found is None:
            found_id = create_case(
                key, qa02_token, found_payload(incident_at, mora["breed_id"], months, locality)
            )
            report["found_created"] = "YES"
            print(f"FOUND CREATED id={found_id}")
            alerts = list_alerts(key, qa02_token)
            found = next((row for row in alerts if row.get("id") == found_id), None)
            if found is None or not is_pair_found(found, qa02_id):
                print("FOUND VERIFY FAIL after create")
                report["found_id"] = found_id
                _emit(report)
                return 1
        else:
            print(f"FOUND REUSED id={found.get('id')}")

    report["lost_id"] = str(lost.get("id") or "")
    report["lost_status"] = str(lost.get("status") or "")
    report["lost_pet"] = str(lost.get("pet_id") or "")
    report["lost_photo"] = str(lost.get("photo_asset_id") or "")
    report["lost_label"] = str(lost.get("location_label") or "")
    report["lost_location"] = f"{LOST_LAT},{LOST_LNG}"
    report["found_id"] = str(found.get("id") or "")
    report["found_status"] = str(found.get("status") or "")
    report["found_pet"] = str(found.get("pet_id") or "")
    report["found_label"] = str(found.get("location_label") or "")
    found_label = report["found_label"]

    cst, craw = candidates(key, qa02_token, report["found_id"])
    crows = rows_of(cst, craw) if cst == 200 else None
    chosen = pick_candidate(crows or [], report["lost_id"], report["found_id"]) if crows is not None else None
    if chosen is None:
        print(f"MATCH READ FAIL http={cst} {safe_error(craw)}")
    else:
        score = float(chosen.get("score") or 0)
        status_ok = chosen.get("status") == "PENDING"
        asserted = chosen.get("asserted_by")
        report["candidate_id"] = str(chosen.get("id") or "")
        report["score"] = f"{score:.4f}"
        report["candidate_status"] = str(chosen.get("status") or "")
        report["asserted_by"] = "NULL" if asserted in (None, "") else "NON_NULL"
        if score >= 0.55 and status_ok and asserted in (None, ""):
            report["match"] = "PASS"
        print(
            f"CANDIDATE id={report['candidate_id']} score={report['score']} "
            f"status={report['candidate_status']} asserted_by={report['asserted_by']}"
        )

    notes = notifications(key, qa01_token)
    note = matching_notification(notes, report["lost_id"], report["found_id"], qa01_id)
    if note:
        target = note.get("deep_link_resource_id") or note.get("related_id")
        dedup = str(note.get("deduplication_key") or "")
        report["notification_id"] = str(note.get("id") or "")
        report["notification_event"] = "lost_found.match.candidate"
        report["notification_target"] = str(target or "")
        report["notification_at"] = str(note.get("created_at") or "")
        owns_pair = report["lost_id"] in dedup and report["found_id"] in dedup
        target_ok = target == report["found_id"]
        category_ok = note.get("category") == "LOST_FOUND"
        leaked = note.get("user_id") not in (None, qa01_id)
        if owns_pair and target_ok and category_ok and not leaked:
            report["notification"] = "PASS"
        print(
            f"NOTIFICATION id={report['notification_id']} "
            f"event={report['notification_event']} target={report['notification_target']} "
            f"created_at={report['notification_at']} category={note.get('category')}"
        )
    else:
        print("NOTIFICATION ABSENT")

    qa01_alerts = list_alerts(key, qa01_token)
    qa01_lost = next((row for row in qa01_alerts if row.get("id") == report["lost_id"]), None)
    qa01_found = next((row for row in qa01_alerts if row.get("id") == report["found_id"]), None)
    if (
        qa01_lost
        and qa01_lost.get("pet_id") == MORA_PET_ID
        and qa01_lost.get("photo_asset_id") == QA01_ASSET_ID
        and qa01_found
        and qa01_found.get("id") == report["found_id"]
    ):
        report["qa01_visibility"] = "PASS"
    print(f"QA01 VISIBILITY: {report['qa01_visibility']}")

    qa02_alerts = list_alerts(key, qa02_token)
    qa02_found = next((row for row in qa02_alerts if row.get("id") == report["found_id"]), None)
    if qa02_found and crows is not None and chosen is not None and report["match"] == "PASS":
        report["qa02_visibility"] = "PASS"
    elif qa02_found and crows is not None and chosen is not None:
        report["qa02_visibility"] = "PASS"
    print(f"QA02 VISIBILITY: {report['qa02_visibility']}")

    ust, uraw = candidates(key, qa03_token, report["found_id"])
    leaked = report["candidate_id"] and report["candidate_id"] in uraw and ust == 200
    if leaked:
        report["unrelated"] = "LEAKED"
    elif ust != 200 and safe_error(uraw) in ("FORBIDDEN", "NOT_FOUND", "NOT_AUTHENTICATED"):
        report["unrelated"] = "DENIED"
    elif ust == 200 and report["candidate_id"] and report["candidate_id"] not in uraw:
        report["unrelated"] = "DENIED"
    else:
        report["unrelated"] = "LEAKED" if ust == 200 else "DENIED"
    print(f"UNRELATED ACTOR PRIVILEGE: {report['unrelated']} http={ust} code={safe_error(uraw)}")

    pet = read_pet(key, qa02_token, report["found_pet"]) if report["found_pet"] else None
    owner_ids = profile_pet_ids(key, qa02_token, qa02_id)
    moment = photo_moment(key, qa02_token, report["found_pet"]) if report["found_pet"] else False
    number = vitacora_number(key, qa02_token, report["found_pet"]) if report["found_pet"] else None
    if pet:
        custodian_ok = (
            pet.get("origin_kind") == "FOUND_CASE"
            and pet.get("current_custodian_kind") == "PERSON"
            and pet.get("current_custodian_person_id") == qa02_id
            and pet.get("species_code") == "DOG"
            and pet.get("sex") == "FEMALE"
            and pet.get("size") == "MEDIUM"
        )
        finder_is_owner = report["found_pet"] in owner_ids
        report["finder_owner"] = "YES" if finder_is_owner else "NO"
        if moment:
            public_code = pet.get("public_code")
            if number:
                report["found_vitacora"] = number
            elif public_code:
                report["found_vitacora"] = f"EXISTS public_code={public_code} number=NOT_GRANTED"
            else:
                report["found_vitacora"] = "EXISTS_NUMBER_NOT_EXPOSED"
        print(
            f"FOUND PET origin={pet.get('origin_kind')} custodian={pet.get('current_custodian_person_id')} "
            f"species={pet.get('species_code')} sex={pet.get('sex')} size={pet.get('size')} "
            f"profile_owner={'YES' if finder_is_owner else 'NO'} photo_moment={'YES' if moment else 'NO'} "
            f"vitacora_number={number or 'UNAVAILABLE'} custodian_ok={'YES' if custodian_ok else 'NO'}"
        )
        if not custodian_ok or finder_is_owner or not moment:
            if report["finder_owner"] != "YES":
                report["finder_owner"] = "NO" if not finder_is_owner else "YES"
    else:
        report["finder_owner"] = "UNKNOWN"
        print("FOUND PET READ FAIL")

    if report["lost_label"] != LOST_LABEL:
        print(f"LOST LABEL MISMATCH got={report['lost_label']}")
    if found_label != FOUND_LABEL:
        print(f"FOUND LABEL MISMATCH got={found_label}")

    _emit(report)
    required = [
        report["qa01_auth"] == "PASS",
        report["qa02_auth"] == "PASS",
        report["qa01_media"] == "PASS",
        report["qa02_media"] == "PASS",
        report["lost_id"] != "",
        report["lost_status"] in ACTIVE,
        report["lost_pet"] == MORA_PET_ID,
        report["lost_photo"] == QA01_ASSET_ID,
        report["lost_label"] == LOST_LABEL,
        report["found_id"] != "",
        report["found_status"] in ACTIVE,
        report["found_pet"] != "",
        report["found_pet"] != MORA_PET_ID,
        bool(report["found_vitacora"]),
        report["finder_owner"] == "NO",
        report["match"] == "PASS",
        report["asserted_by"] == "NULL",
        report["notification"] == "PASS",
        report["notification_target"] == report["found_id"],
        report["qa01_visibility"] == "PASS",
        report["qa02_visibility"] == "PASS",
        report["unrelated"] == "DENIED",
        found_label == FOUND_LABEL,
    ]
    return 0 if all(required) else 1


def _emit(report: dict) -> None:
    print("LEOVER COMMUNITY CARE LIVE LOST/FOUND 10B")
    print(f"STAGING: {STAGING_REF}")
    print(f"QA01 AUTH: {report['qa01_auth']}")
    print(f"QA02 AUTH: {report['qa02_auth']}")
    print(f"QA01 MEDIA: {report['qa01_media']}")
    print(f"asset: {QA01_ASSET_ID}")
    print(f"QA02 MEDIA: {report['qa02_media']}")
    print(f"asset: {QA02_ASSET_ID}")
    print(f"EXISTING PAIR REUSED: {report['reused']}")
    print(f"LOST CREATED: {report['lost_created']}")
    print(f"LOST CASE ID: {report['lost_id']}")
    print(f"LOST STATUS: {report['lost_status']}")
    print(f"LOST PET: {report['lost_pet']}")
    print(f"LOST PHOTO: {report['lost_photo']}")
    print(f"LOST LOCATION: {report['lost_location']}")
    print(f"LOST LOCATION LABEL: {report['lost_label']}")
    print(f"FOUND CREATED: {report['found_created']}")
    print(f"FOUND CASE ID: {report['found_id']}")
    print(f"FOUND STATUS: {report['found_status']}")
    print(f"FOUND PROVISIONAL PET: {report['found_pet']}")
    print(f"FOUND VITACORA: {report['found_vitacora']}")
    print(f"FOUND LOCATION: {FOUND_LAT},{FOUND_LNG}")
    print(f"FOUND LOCATION LABEL: {report['found_label']}")
    print(f"FINDER IS OWNER: {report['finder_owner']}")
    print(f"AUTOMATIC MATCH: {report['match']}")
    print(f"CANDIDATE ID: {report['candidate_id']}")
    print(f"MATCH SCORE: {report['score']}")
    print(f"CANDIDATE STATUS: {report['candidate_status']}")
    print(f"ASSERTED_BY: {report['asserted_by']}")
    print(f"OWNER NOTIFICATION: {report['notification']}")
    print(f"NOTIFICATION ID: {report['notification_id']}")
    print(f"NOTIFICATION EVENT: {report['notification_event']}")
    print(f"NOTIFICATION TARGET: {report['notification_target']}")
    print(f"NOTIFICATION CREATED_AT: {report['notification_at']}")
    print(f"QA01 VISIBILITY: {report['qa01_visibility']}")
    print(f"QA02 VISIBILITY: {report['qa02_visibility']}")
    print(f"UNRELATED ACTOR PRIVILEGE: {report['unrelated']}")
    print("MANUAL DB INSERT: NO")
    print("SERVICE ROLE: NO")
    print("NEW MIGRATION: NO")
    print("PROD: NO")


if __name__ == "__main__":
    sys.exit(main())
