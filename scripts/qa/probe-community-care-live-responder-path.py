#!/usr/bin/env python3
"""Idempotent STAGING probe for one responder-care FOUND.

Publishes at most one FOUND for QA02, lets QA03 claim it, then marks
IN_CARE. A later run that already sees IN_CARE only reads.

User sessions only:
  SUPABASE_STAGING_PUBLISHABLE_KEY
  LEOVER_QA_PASSWORD

Staging project: tobqbddfcyitwgbkthhy.
Refuses the legacy/prod ref. Does not use service_role, a database
password, or SQL inserts.

Photo: lost_found_alerts.photo_asset_id, pets.avatar_asset_id, and
vitacora_moments.asset_id are non-unique foreign keys. One READY image
may back another FOUND. This probe reuses QA02's canonical asset.

This fixture is not an owner-validation case. It does not publish a
lost alert and it does not call the owner-assert or custodian-confirm
RPCs. The resolved 10B pair is read and then left alone.
"""

from __future__ import annotations

import base64
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

STAGING_REF = "tobqbddfcyitwgbkthhy"
LEGACY_PROD_REF = "wystsapjfpdtoprlmizz"
BASE = f"https://{STAGING_REF}.supabase.co"

QA02_ASSET_ID = "c84dc7c2-9810-4d99-8c11-6a2caad89e95"
# Filled after the first live create. Empty keeps note identity.
PINNED_FOUND_ID = ""
RESOLVED_LOST_ID = "232c473b-cbf3-485f-8f89-cb27132c8f89"
RESOLVED_FOUND_ID = "2e0cf009-325d-4db0-be03-4ca48c1b2f67"
NOTE_MARK = "QA LIVE 12 RESPONDER PATH"
FOUND_LABEL = "QA - Responder Path 12, CABA"
# ~65m from the QA03 synthetic base (-34.6001, -58.3816).
FOUND_LAT = -34.60055
FOUND_LNG = -58.38205
LOCALITY_CANDIDATE = "loc-ar-loc-caba"
ACTIVE = {"OPEN", "CLAIMED", "IN_CARE"}

ACTORS = {
    "QA01": "qa01owner",
    "QA02": "qa02finder",
    "QA03": "qa03rescuer",
    "QA04": "qa04rescuer2",
    "QA06": "qa06foster",
}


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
            "ALERT_ALREADY_CLAIMED",
            "ALERT_CLAIM_NOT_NEAREST",
            "CLAIM_FOUND_ONLY",
            "LF-CREATE-AUTH",
            "LF-CREATE-KIND",
            "LF-CREATE-FORBIDDEN",
            "LF-CREATE-LOCATION",
            "LF-CREATE-IDENTITY",
            "LF-CREATE-ALERT",
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


def by_id(rows: list[dict], case_id: str) -> dict | None:
    return next((row for row in rows if row.get("id") == case_id), None)


def fixture_rows(alerts: list[dict], finder_id: str) -> list[dict]:
    chosen = []
    seen = set()
    for row in alerts:
        if row.get("kind") != "FOUND" or row.get("status") not in ACTIVE:
            continue
        if row.get("created_by") != finder_id:
            continue
        marked = (
            NOTE_MARK in str(row.get("note") or "")
            and row.get("location_label") == FOUND_LABEL
        )
        pinned = bool(PINNED_FOUND_ID) and row.get("id") == PINNED_FOUND_ID
        if not marked and not pinned:
            continue
        if row.get("id") in seen:
            continue
        seen.add(row.get("id"))
        chosen.append(row)
    return chosen


def responder_base(key: str, token: str) -> dict:
    status, raw = rpc(key, token, "canon_get_my_responder_base", {})
    body = parse_json(raw) if status == 200 else None
    if not isinstance(body, dict) or "eligible" not in body:
        raise SystemExit(f"ELIGIBILITY FAIL http={status} {safe_error(raw)}")
    return body


def notifications(key: str, token: str) -> list[dict]:
    status, raw = rpc(key, token, "canon_list_my_notifications", {"p_limit": 200})
    rows = rows_of(status, raw)
    if rows is None:
        raise SystemExit(f"NOTIFICATIONS FAIL http={status} {safe_error(raw)}")
    return [row for row in rows if isinstance(row, dict)]


def notes_for_case(rows: list[dict], case_id: str) -> list[dict]:
    matched = [row for row in rows if case_id in json.dumps(row)]
    matched.sort(key=lambda row: str(row.get("created_at") or ""))
    return matched


def profile_pet_ids(key: str, token: str, user_id: str) -> list[str]:
    status, raw = rpc(key, token, "canon_list_pets_for_person_profile", {"p_user_id": user_id})
    rows = rows_of(status, raw)
    if rows is None:
        return []
    return [str(row.get("id")) for row in rows if isinstance(row, dict) and row.get("id")]


def pet_record(key: str, token: str, pet_id: str) -> dict | None:
    status, raw = http(
        key,
        "GET",
        "/rest/v1/pets?select=id,name,species_code,sex,size,origin_kind,lifecycle_status,"
        "archived_at,avatar_asset_id,current_custodian_kind,current_custodian_person_id,"
        "created_by_user_id"
        "&id=eq." + urllib.parse.quote(pet_id),
        token=token,
    )
    rows = rows_of(status, raw) if status == 200 else None
    return rows[0] if rows else None


def moment_rows(key: str, token: str, pet_id: str) -> list[dict] | None:
    status, raw = rpc(key, token, "canon_list_vitacora_moments", {"p_pet_id": pet_id})
    rows = rows_of(status, raw)
    if rows is None:
        return None
    return [row for row in rows if isinstance(row, dict)]


def rpc_rows(key: str, token: str, name: str, body: dict | None):
    status, raw = rpc(key, token, name, body)
    rows = rows_of(status, raw) if status == 200 else None
    return status, rows, "" if status == 200 else safe_error(raw)


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


def read_asset(key: str, token: str, asset_id: str, user_id: str) -> dict:
    status, raw = http(
        key,
        "GET",
        "/rest/v1/media_assets"
        "?select=id,mime_type,lifecycle_status,owner_person_id,created_by,bucket"
        f"&id=eq.{urllib.parse.quote(asset_id)}",
        token=token,
    )
    rows = rows_of(status, raw) if status == 200 else None
    row = rows[0] if rows else None
    if not row or row.get("id") != asset_id:
        raise SystemExit(f"PHOTO READ FAIL http={status} {safe_error(raw)}")
    if row.get("lifecycle_status") != "READY" or row.get("mime_type") != "image/png":
        raise SystemExit("PHOTO READ FAIL asset is not a READY image")
    if row.get("owner_person_id") != user_id or row.get("created_by") != user_id:
        raise SystemExit("PHOTO READ FAIL asset is not owned by QA02")
    return row


def resolved_pair_ok(key: str, token: str, alerts: list[dict]) -> bool:
    if by_id(alerts, RESOLVED_LOST_ID) or by_id(alerts, RESOLVED_FOUND_ID):
        print("RESOLVED PAIR: LEAKED_INTO_ACTIVE_LIST")
        return False
    status, raw = rpc(
        key, token, "canon_list_found_match_candidates", {"p_found_id": RESOLVED_FOUND_ID}
    )
    rows = rows_of(status, raw) if status == 200 else None
    if rows is None:
        print(f"RESOLVED PAIR READ FAIL http={status} {safe_error(raw)}")
        return False
    owner = next((row for row in rows if row.get("match_reason") == "OWNER_ASSERT"), None)
    auto = next((row for row in rows if row.get("id") == "d3488929-afd1-489b-bac8-fd8e33e6093f"), None)
    ok = bool(
        owner
        and owner.get("status") == "ACCEPTED"
        and owner.get("lost_alert_id") == RESOLVED_LOST_ID
        and auto
        and auto.get("status") == "REJECTED"
    )
    print(
        "RESOLVED PAIR: "
        f"{'UNTOUCHED' if ok else 'CHANGED'} "
        f"lost={RESOLVED_LOST_ID} found={RESOLVED_FOUND_ID} "
        f"owner={None if not owner else owner.get('status')} "
        f"auto={None if not auto else auto.get('status')}"
    )
    return ok


def found_payload(incident_at: str, locality: str | None) -> dict:
    return {
        "p_kind": "FOUND",
        "p_pet_id": None,
        "p_locality_id": locality,
        "p_species": "DOG",
        "p_note": NOTE_MARK,
        "p_lat": FOUND_LAT,
        "p_lng": FOUND_LNG,
        "p_incident_at": incident_at,
        "p_photo_asset_id": QA02_ASSET_ID,
        "p_name": None,
        "p_sex": "UNKNOWN",
        "p_size": "MEDIUM",
        "p_estimated_age_months": None,
        "p_breed_id": None,
        "p_location_label": FOUND_LABEL,
    }


def create_case(key: str, token: str, payload: dict) -> str:
    status, raw = rpc(key, token, "canon_create_lost_found", payload)
    if status != 200:
        raise SystemExit(f"CREATE FAIL http={status} {safe_error(raw)}")
    body = parse_json(raw)
    case_id = body if isinstance(body, str) else None
    if isinstance(body, dict):
        case_id = body.get("id") or body.get("canon_create_lost_found")
    if not case_id or not isinstance(case_id, str):
        raise SystemExit("CREATE FAIL missing id")
    if case_id in (RESOLVED_FOUND_ID, RESOLVED_LOST_ID):
        raise SystemExit("CREATE FAIL returned the resolved pair")
    return case_id


def expect_denied(key: str, token: str, label: str, rpc_name: str, body: dict, allowed: set[str]) -> bool:
    status, raw = rpc(key, token, rpc_name, body)
    code = "SUCCESS" if status == 200 else safe_error(raw)
    denied = status != 200 and code in allowed
    print(f"{label}: {'DENIED' if denied else 'LEAKED'} http={status} code={code}")
    return denied


def describe_note(row: dict) -> str:
    return (
        f"id={row.get('id')} title={row.get('title')} "
        f"dedup={row.get('deduplication_key')} "
        f"target={row.get('deep_link_resource_id')} "
        f"created_at={row.get('created_at')}"
    )


def for_pet(rows: list[dict] | None, pet_id: str) -> list[dict] | None:
    if rows is None:
        return None
    return [row for row in rows if row.get("pet_id") in (None, pet_id)]


def side_effects(key: str, token: str, pet_id: str) -> dict:
    moments = moment_rows(key, token, pet_id) or []
    kinds = sorted({str(row.get("kind")) for row in moments})
    pub_status, publications, _pub_err = rpc_rows(key, token, "canon_list_adoptions", {})
    transfer_status, transfers, _transfer_err = rpc_rows(
        key, token, "canon_list_care_transfers", {"p_pet_id": pet_id}
    )
    incoming_status, incoming, _incoming_err = rpc_rows(
        key, token, "canon_list_incoming_care_transfers", {}
    )
    place_status, placements, _place_err = rpc_rows(key, token, "canon_list_foster_placements", {})
    return {
        "moments": moments,
        "kinds": kinds,
        "publications": for_pet(publications, pet_id),
        "pub_status": pub_status,
        "transfers": transfers,
        "transfer_status": transfer_status,
        "incoming": for_pet(incoming, pet_id),
        "incoming_status": incoming_status,
        "placements": for_pet(placements, pet_id),
        "place_status": place_status,
    }


def main() -> int:
    refuse_non_staging()
    key = os.environ.get("SUPABASE_STAGING_PUBLISHABLE_KEY", "").strip()
    password = os.environ.get("LEOVER_QA_PASSWORD", "").strip()
    if not key or not password:
        print("MISSING_SECRET SUPABASE_STAGING_PUBLISHABLE_KEY or LEOVER_QA_PASSWORD")
        return 2
    assert_publishable_key(key)
    force_read = "--read-only" in sys.argv

    sessions: dict[str, tuple[str, str]] = {}
    try:
        for label, username in ACTORS.items():
            sessions[label] = login(key, password, username)
            print(f"{label} AUTH: PASS")
    except SystemExit as exc:
        print(exc)
        return 1

    qa02_token, qa02_id = sessions["QA02"]
    qa03_token, qa03_id = sessions["QA03"]
    qa04_token, qa04_id = sessions["QA04"]
    qa06_token, qa06_id = sessions["QA06"]
    qa01_token, qa01_id = sessions["QA01"]

    bases = {}
    for label, token in (
        ("QA03", qa03_token),
        ("QA06", qa06_token),
        ("QA04", qa04_token),
        ("QA02", qa02_token),
        ("QA01", qa01_token),
    ):
        bases[label] = responder_base(key, token)
        row = bases[label]
        print(
            f"{label} ELIGIBLE: {row.get('eligible')} "
            f"lat={row.get('lat')} lng={row.get('lng')}"
        )
    if bases["QA03"].get("eligible") is not True or bases["QA06"].get("eligible") is not False:
        print("ELIGIBILITY PRECONDITION FAIL")
        return 1
    if bases["QA01"].get("eligible") is not False or bases["QA02"].get("eligible") is not False:
        print("PERSONAL ELIGIBILITY PRECONDITION FAIL")
        return 1

    alerts = list_alerts(key, qa02_token)
    if not resolved_pair_ok(key, qa02_token, alerts):
        return 1
    read_asset(key, qa02_token, QA02_ASSET_ID, qa02_id)
    print(f"PHOTO ASSET: REUSED {QA02_ASSET_ID}")
    print("PHOTO CONTRACT: REUSE_VALID")

    matches = fixture_rows(alerts, qa02_id)
    if len(matches) > 1:
        print("REFUSING DUPLICATE FOUND")
        return 1
    created = False
    if not matches:
        if force_read:
            print("READ ONLY REFUSED fixture absent")
            return 1
        incident_at = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
        locality = locality_id(key, qa02_token)
        case_id = create_case(key, qa02_token, found_payload(incident_at, locality))
        created = True
        alerts = list_alerts(key, qa02_token)
        matches = fixture_rows(alerts, qa02_id)
        if len(matches) != 1:
            alerts = list_alerts(key, qa02_token)
            matches = fixture_rows(alerts, qa02_id)
        if len(matches) != 1 or matches[0].get("id") != case_id:
            print("REFUSING DUPLICATE FOUND")
            print(f"CREATE VERIFY FAIL id={case_id} matches={len(matches)}")
            return 1
        print(f"FOUND CREATED: YES id={case_id}")
    else:
        print(f"FOUND CREATED: NO reused={matches[0].get('id')}")

    case = matches[0]
    case_id = str(case.get("id"))
    if case_id in (RESOLVED_FOUND_ID, RESOLVED_LOST_ID):
        print("REFUSING RESOLVED PAIR")
        return 1
    if case.get("status") == "IN_CARE" and not created:
        mode = "READ"
    elif force_read:
        print(f"READ ONLY REFUSED status={case.get('status')}")
        return 1
    else:
        mode = "MUTATE"
    print(f"MODE: {mode}")

    if case.get("photo_asset_id") != QA02_ASSET_ID or case.get("location_label") != FOUND_LABEL:
        print("FOUND SHAPE FAIL photo or label")
        return 1
    if case.get("status") not in ACTIVE:
        print(f"FOUND STATUS FAIL {case.get('status')}")
        return 1

    pet_id = str(case.get("pet_id") or "")
    pet = pet_record(key, qa02_token, pet_id) if pet_id else None
    moments = moment_rows(key, qa02_token, pet_id) if pet_id else None
    if not pet or moments is None:
        print("FOUND IDENTITY FAIL pet or vitacora missing")
        return 1
    print(
        f"IDENTITY pet={pet_id} name={pet.get('name')} species={pet.get('species_code')} "
        f"sex={pet.get('sex')} size={pet.get('size')} origin={pet.get('origin_kind')} "
        f"lifecycle={pet.get('lifecycle_status')} vitacora_pet={pet_id} "
        f"custodian={pet.get('current_custodian_person_id')}"
    )
    if (
        pet.get("origin_kind") != "FOUND_CASE"
        or pet.get("species_code") != "DOG"
        or pet.get("sex") != "UNKNOWN"
        or pet.get("size") != "MEDIUM"
        or pet.get("name") != "Sin nombre"
        or pet.get("avatar_asset_id") != QA02_ASSET_ID
        or pet.get("created_by_user_id") != qa02_id
        or not any(row.get("kind") == "CARE_CREATED" for row in moments)
    ):
        print("FOUND ATTRIBUTES FAIL")
        return 1

    qa02_owner = pet_id in profile_pet_ids(key, qa02_token, qa02_id)
    qa03_owner = pet_id in profile_pet_ids(key, qa03_token, qa03_id)
    print(f"FINDER IS OWNER: {'YES' if qa02_owner else 'NO'}")
    print(f"QA03 IS OWNER: {'YES' if qa03_owner else 'NO'}")

    def reload() -> dict:
        rows = fixture_rows(list_alerts(key, qa02_token), qa02_id)
        if len(rows) != 1 or rows[0].get("id") != case_id:
            raise SystemExit("REFUSING DUPLICATE FOUND")
        return rows[0]

    claim_result = None
    recipient_rows: list[dict] = []
    qa03_notes_after_fanout: list[dict] = []
    new_after_claim: list[dict] = []
    new_after_care: list[dict] = []
    second_claim = "NOT_TESTED"
    pre_denied = True

    if mode == "MUTATE" and case.get("status") == "OPEN":
        if pet.get("current_custodian_person_id") != qa02_id or qa02_owner or qa03_owner:
            print("OPEN CUSTODY FAIL")
            return 1
        if pet.get("lifecycle_status") != "ACTIVE":
            print("OPEN PET LIFECYCLE FAIL")
            return 1
        print(f"INITIAL STATUS: {case.get('status')}")
        qa03_before = {str(row.get("id")) for row in notifications(key, qa03_token)}
        qa03_case = by_id(list_alerts(key, qa03_token), case_id)
        qa06_case = by_id(list_alerts(key, qa06_token), case_id)
        qa04_case = by_id(list_alerts(key, qa04_token), case_id)
        qa01_case = by_id(list_alerts(key, qa01_token), case_id)
        qa02_case = by_id(list_alerts(key, qa02_token), case_id)
        print(f"QA03 CAN_CLAIM: {'YES' if qa03_case and qa03_case.get('can_claim') is True else 'NO'}")
        print(f"QA06 CAN_CLAIM: {'YES' if qa06_case and qa06_case.get('can_claim') is True else 'NO'}")
        print(f"QA04 CAN_CLAIM: {'YES' if qa04_case and qa04_case.get('can_claim') is True else 'NO'}")
        print(f"QA02 CAN_CLAIM: {'YES' if qa02_case and qa02_case.get('can_claim') is True else 'NO'}")
        print(f"QA01 CAN_CLAIM: {'YES' if qa01_case and qa01_case.get('can_claim') is True else 'NO'}")
        status, raw = rpc(key, qa02_token, "canon_list_lost_found_recipients", {"p_alert_id": case_id})
        recipient_rows = rows_of(status, raw) or []
        if status != 200:
            print(f"RECIPIENTS FAIL http={status} {safe_error(raw)}")
            return 1
        print(f"RECIPIENTS: {len(recipient_rows)}")
        for row in recipient_rows:
            print(
                "RECIPIENT "
                f"rank={row.get('rank')} distance_meters={row.get('distance_meters')} "
                f"status={row.get('status')} notified_at={row.get('notified_at')}"
            )
        print("RECIPIENT WAVE: NOT_EXPOSED")
        qa03_notes_after_fanout = notes_for_case(notifications(key, qa03_token), case_id)
        qa06_notes = notes_for_case(notifications(key, qa06_token), case_id)
        qa04_notes = notes_for_case(notifications(key, qa04_token), case_id)
        print(f"QA03 NOTIFICATIONS AFTER FANOUT: {len(qa03_notes_after_fanout)}")
        for row in qa03_notes_after_fanout:
            print("QA03 NOTE " + describe_note(row))
        print(f"QA06 NOTIFICATIONS FOR CASE: {len(qa06_notes)}")
        print(f"QA04 NOTIFICATIONS FOR CASE: {len(qa04_notes)}")
        for row in qa04_notes:
            print("QA04 NOTE " + describe_note(row))
        if not qa03_case or qa03_case.get("can_claim") is not True:
            print("QA03 CAN_CLAIM FAIL")
            return 1
        if qa06_case and qa06_case.get("can_claim") is True:
            print("FOSTER EXCLUSION FAIL")
            return 1
        if qa06_notes:
            print("FOSTER EXCLUSION FAIL notification")
            return 1
        if (qa02_case and qa02_case.get("can_claim") is True) or (
            qa01_case and qa01_case.get("can_claim") is True
        ):
            print("PERSONAL CAN_CLAIM LEAK")
            return 1
        statuses = {str(row.get("status")) for row in recipient_rows}
        if statuses != {"NOTIFIED"}:
            print(f"RECIPIENT STATUS UNEXPECTED {sorted(statuses)}")
            return 1
        pre_denied = all(
            [
                expect_denied(
                    key, qa02_token, "QA02 CLAIM", "canon_claim_lost_found",
                    {"p_id": case_id}, {"FORBIDDEN"},
                ),
                expect_denied(
                    key, qa06_token, "QA06 CLAIM BEFORE", "canon_claim_lost_found",
                    {"p_id": case_id}, {"FORBIDDEN"},
                ),
                expect_denied(
                    key, qa01_token, "QA01 CLAIM", "canon_claim_lost_found",
                    {"p_id": case_id}, {"FORBIDDEN"},
                ),
            ]
        )
        case = reload()
        if not pre_denied or case.get("status") != "OPEN" or case.get("claimed_by") not in (None, ""):
            print("PRE-CLAIM DENIAL CHANGED THE CASE")
            return 1
        notes_before_claim = {str(row.get("id")) for row in notifications(key, qa03_token)}
        status, raw = rpc(key, qa03_token, "canon_claim_lost_found", {"p_id": case_id})
        claim_result = parse_json(raw) if status == 200 else None
        print(f"QA03 CLAIM: {'PASS' if status == 200 else 'FAIL'} http={status} code={safe_error(raw) if status != 200 else 'OK'}")
        if not isinstance(claim_result, dict):
            return 1
        print(
            "CLAIM RESULT "
            f"status={claim_result.get('status')} pet_id={claim_result.get('pet_id')} "
            f"claimed_by={claim_result.get('claimed_by')}"
        )
        case = reload()
        pet_after = pet_record(key, qa03_token, pet_id)
        moments_after = moment_rows(key, qa03_token, pet_id)
        if (
            claim_result.get("status") != "CLAIMED"
            or claim_result.get("pet_id") != pet_id
            or claim_result.get("claimed_by") != qa03_id
            or case.get("status") != "CLAIMED"
            or case.get("claimed_by") != qa03_id
            or not pet_after
            or pet_after.get("id") != pet_id
            or pet_after.get("current_custodian_person_id") != qa03_id
            or pet_after.get("lifecycle_status") != "ACTIVE"
            or moments_after is None
            or not any(row.get("kind") == "CARE_CREATED" for row in moments_after)
        ):
            print("CLAIM STATE FAIL")
            return 1
        if pet_id in profile_pet_ids(key, qa03_token, qa03_id):
            print("QA03 BECAME OWNER")
            return 1
        print(f"STATUS AFTER CLAIM: {case.get('status')}")
        print(f"CLAIMED_BY: {case.get('claimed_by')}")
        print(f"CLAIMED_AT: {case.get('claimed_at')}")
        print(f"CUSTODIAN AFTER CLAIM: {pet_after.get('current_custodian_person_id')}")
        new_after_claim = [
            row for row in notes_for_case(notifications(key, qa03_token), case_id)
            if str(row.get("id")) not in notes_before_claim and str(row.get("id")) not in qa03_before
        ]
        print(f"NEW QA03 NOTIFICATIONS AFTER CLAIM: {len(new_after_claim)}")
        for row in new_after_claim:
            print("CLAIM NOTE " + describe_note(row))
        qa06_ok = expect_denied(
            key, qa06_token, "QA06 CLAIM AFTER", "canon_claim_lost_found",
            {"p_id": case_id}, {"FORBIDDEN", "ALERT_ALREADY_CLAIMED", "ALERT_CLAIM_NOT_NEAREST"},
        )
        qa04_ok = expect_denied(
            key, qa04_token, "QA04 CLAIM AFTER", "canon_claim_lost_found",
            {"p_id": case_id}, {"FORBIDDEN", "ALERT_ALREADY_CLAIMED", "ALERT_CLAIM_NOT_NEAREST"},
        )
        case = reload()
        if not qa06_ok or not qa04_ok or case.get("status") != "CLAIMED" or case.get("claimed_by") != qa03_id:
            print("SECOND CLAIM CHANGED THE CASE")
            second_claim = "LEAKED"
            return 1
        second_claim = "DENIED"
        print("SECOND CLAIM: DENIED")

    if mode == "MUTATE" and case.get("status") == "CLAIMED":
        if case.get("claimed_by") != qa03_id:
            print("CLAIMED BY UNEXPECTED ACTOR")
            return 1
        held = pet_record(key, qa03_token, pet_id)
        if not held or held.get("current_custodian_person_id") != qa03_id:
            print("CLAIMED CUSTODIAN FAIL")
            return 1
        if not expect_denied(
            key, qa02_token, "QA02 IN_CARE", "canon_mark_lost_found_in_care",
            {"p_id": case_id}, {"FORBIDDEN"},
        ):
            return 1
        case = reload()
        if case.get("status") != "CLAIMED":
            print("UNAUTHORIZED IN_CARE CHANGED THE CASE")
            return 1
        notes_before_care = {str(row.get("id")) for row in notifications(key, qa03_token)}
        status, raw = rpc(key, qa03_token, "canon_mark_lost_found_in_care", {"p_id": case_id})
        body = parse_json(raw) if status == 200 else None
        print(f"IN_CARE: {'PASS' if status == 200 else 'FAIL'} http={status} code={safe_error(raw) if status != 200 else 'OK'}")
        if not isinstance(body, dict) or body.get("status") != "IN_CARE":
            return 1
        case = reload()
        new_after_care = [
            row for row in notes_for_case(notifications(key, qa03_token), case_id)
            if str(row.get("id")) not in notes_before_care
        ]
        print(f"NEW QA03 NOTIFICATIONS AFTER IN_CARE: {len(new_after_care)}")
        for row in new_after_care:
            print("IN_CARE NOTE " + describe_note(row))

    if case.get("status") != "IN_CARE":
        print(f"FINAL STATUS UNEXPECTED {case.get('status')}")
        return 1

    final_pet = pet_record(key, qa03_token, pet_id)
    effects = side_effects(key, qa03_token, pet_id)
    qa03_owner = pet_id in profile_pet_ids(key, qa03_token, qa03_id)
    qa02_owner = pet_id in profile_pet_ids(key, qa02_token, qa02_id)
    qa03_view = by_id(list_alerts(key, qa03_token), case_id)
    qa02_view = by_id(list_alerts(key, qa02_token), case_id)
    qa06_view = by_id(list_alerts(key, qa06_token), case_id)
    final_notes = notes_for_case(notifications(key, qa03_token), case_id)
    qa06_final_notes = notes_for_case(notifications(key, qa06_token), case_id)
    photo_moments = [
        row for row in effects["moments"]
        if row.get("kind") == "PHOTO" and row.get("title") == "Foto del hallazgo"
    ]
    care_created = [row for row in effects["moments"] if row.get("kind") == "CARE_CREATED"]
    transfers_in_history = [row for row in effects["moments"] if row.get("kind") == "CARE_TRANSFER"]
    print(f"FINAL STATUS: {case.get('status')}")
    print(f"FINAL CLAIMED_BY: {case.get('claimed_by')}")
    print(f"FINAL CLAIMED_AT: {case.get('claimed_at')}")
    print(
        "FINAL CUSTODIAN: "
        f"{None if not final_pet else final_pet.get('current_custodian_person_id')} "
        f"kind={None if not final_pet else final_pet.get('current_custodian_kind')}"
    )
    print(
        "FINAL PET: "
        f"lifecycle={None if not final_pet else final_pet.get('lifecycle_status')} "
        f"archived_at={None if not final_pet else final_pet.get('archived_at')}"
    )
    vita_open = len(care_created) == 1 and bool(final_pet and final_pet.get("lifecycle_status") == "ACTIVE")
    print(
        "VITACORA FINAL: "
        f"{'OPEN' if vita_open else 'OTHER'} profile_pet={pet_id} "
        "number=NOT_GRANTED_ON_USER_SESSION"
    )
    print(f"MOMENT KINDS: {','.join(effects['kinds']) or 'NONE'}")
    print(f"PHOTO MOMENTS: {len(photo_moments)}")
    print(f"CARE_CREATED MOMENTS: {len(care_created)}")
    print(f"CARE_TRANSFER MOMENTS: {len(transfers_in_history)}")
    print(
        "ADOPTION PUBLICATIONS: "
        f"http={effects['pub_status']} count={None if effects['publications'] is None else len(effects['publications'])}"
    )
    print(
        "CARE TRANSFERS: "
        f"http={effects['transfer_status']} count={None if effects['transfers'] is None else len(effects['transfers'])}"
    )
    print(
        "INCOMING TRANSFERS: "
        f"http={effects['incoming_status']} count={None if effects['incoming'] is None else len(effects['incoming'])}"
    )
    print(
        "FOSTER PLACEMENTS: "
        f"http={effects['place_status']} count={None if effects['placements'] is None else len(effects['placements'])}"
    )
    print("OWNER ROLE: profile list excludes AUTHORIZED custodians")
    print(f"QA03 NOTIFICATIONS FOR CASE: {len(final_notes)}")
    for row in final_notes:
        print("FINAL NOTE " + describe_note(row))
    print(f"QA06 NOTIFICATIONS FOR CASE: {len(qa06_final_notes)}")
    print(f"QA03 IS_CUSTODIAN: {None if not qa03_view else qa03_view.get('is_custodian')}")
    print(f"QA02 IS_CUSTODIAN: {None if not qa02_view else qa02_view.get('is_custodian')}")
    print(f"QA06 CASE VISIBLE: {'YES' if qa06_view else 'NO'}")
    print(f"FINDER IS OWNER FINAL: {'YES' if qa02_owner else 'NO'}")
    print(f"QA03 IS OWNER FINAL: {'YES' if qa03_owner else 'NO'}")
    alerts = list_alerts(key, qa02_token)
    pair_ok = resolved_pair_ok(key, qa02_token, alerts)
    still_one = len(fixture_rows(alerts, qa02_id)) == 1

    checks = {
        "pair": pair_ok,
        "single": still_one,
        "status": case.get("status") == "IN_CARE",
        "claimed_by": case.get("claimed_by") == qa03_id,
        "custodian": bool(final_pet and final_pet.get("current_custodian_person_id") == qa03_id),
        "pet_active": bool(final_pet and final_pet.get("lifecycle_status") == "ACTIVE" and not final_pet.get("archived_at")),
        "same_pet": bool(final_pet and final_pet.get("id") == pet_id),
        "vita_open": vita_open,
        "finder_not_owner": not qa02_owner,
        "responder_not_owner": not qa03_owner,
        "qa03_custodian_flag": bool(qa03_view and qa03_view.get("is_custodian") is True),
        "qa02_not_custodian": bool(qa02_view and qa02_view.get("is_custodian") is False),
        "no_transfer_history": len(transfers_in_history) == 0,
        "no_adoption": effects["publications"] == [],
        "no_transfer_rows": effects["transfers"] == [] and effects["incoming"] == [],
        "no_foster_placement": effects["placements"] == [],
        "photo_moment": len(photo_moments) == 1,
        "care_created": len(care_created) == 1,
        "qa06_silent": len(qa06_final_notes) == 0,
        "pre_denied": pre_denied if mode == "MUTATE" else True,
    }
    if mode == "MUTATE":
        checks["second_claim"] = second_claim == "DENIED"
        checks["fanout_note"] = len(qa03_notes_after_fanout) >= 1
    failed = [name for name, ok in checks.items() if not ok]
    print(f"CHECKS: {'PASS' if not failed else 'FAIL'} failed={','.join(failed) or 'NONE'}")
    print(f"FOUND CASE ID: {case_id}")
    print(f"PROVISIONAL PET: {pet_id}")
    print(f"VITACORA: {pet_id}")
    print(f"QA02 USER: {qa02_id}")
    print(f"QA03 USER: {qa03_id}")
    print(f"QA04 USER: {qa04_id}")
    print(f"QA06 USER: {qa06_id}")
    print(f"QA01 USER: {qa01_id}")
    print("OWNER ASSERTION: NOT RUN")
    print("MANUAL SQL: NO")
    print("SERVICE ROLE: NO")
    print("NEW MIGRATION: NO")
    print("PROD: NO")
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
