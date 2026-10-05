#!/usr/bin/env python3
"""STAGING authorization probe for an already ACCEPTED care transfer.

User sessions only:
  SUPABASE_STAGING_PUBLISHABLE_KEY
  LEOVER_QA_PASSWORD

Staging project: tobqbddfcyitwgbkthhy.
Refuses the legacy/prod ref. Does not use service_role, an access token,
or SQL. Does not create a publication, application, transfer, or alert.

The only product call that may succeed is canon_accept_care_transfer by
the actual recipient. That call must be idempotent.
"""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request

STAGING_REF = "tobqbddfcyitwgbkthhy"
LEGACY_PROD_REF = "wystsapjfpdtoprlmizz"
BASE = f"https://{STAGING_REF}.supabase.co"

PET_ID = "73e9997e-27fa-4265-946f-36001f27f815"
PUBLICATION_ID = "e25e7074-caa3-4d0b-aa87-8bbe65a993e5"
TRANSFER_ID = "7dcae39a-b8a2-4d10-9fc0-ea16d287a830"

ACTORS = {
    "QA14": "qa14adopter",
    "QA15": "qa15adopter2",
    "QA01": "qa01owner",
    "QA07": "qa07shelter",
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


def error_code(raw: str) -> str:
    body = parse_json(raw)
    if isinstance(body, dict):
        message = str(body.get("message") or body.get("msg") or "")
        code = str(body.get("code") or "")
        upper = message.upper()
        for marker in ("NOT_AUTHENTICATED", "FORBIDDEN", "NOT_FOUND", "PET_TRANSFER_NOT_PENDING"):
            if marker in upper or marker in code:
                return marker
        if message:
            return message[:160]
        if code:
            return code[:80]
    return "http_error"


def login(key: str, password: str, username: str) -> tuple[str, str]:
    status, raw = http(
        key,
        "POST",
        "/rest/v1/rpc/canon_begin_username_login",
        {"p_username": username, "p_password": password},
    )
    if status != 200:
        raise SystemExit(f"{username} AUTH FAIL begin http={status} {error_code(raw)}")
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


def rpc(key: str, token: str, name: str, body: dict | None = None):
    return http(key, "POST", f"/rest/v1/rpc/{name}", {} if body is None else body, token=token)


def as_rows(status: int, raw: str, label: str) -> list:
    if status != 200:
        raise SystemExit(f"{label} FAIL http={status} {error_code(raw)}")
    body = parse_json(raw)
    if body is None:
        return []
    if isinstance(body, list):
        return body
    raise SystemExit(f"{label} FAIL unexpected list")


def as_object(status: int, raw: str, label: str) -> dict:
    if status != 200:
        raise SystemExit(f"{label} FAIL http={status} {error_code(raw)}")
    body = parse_json(raw)
    if not isinstance(body, dict):
        raise SystemExit(f"{label} FAIL unexpected object")
    return body


def denied(status: int, raw: str) -> bool:
    if status == 200:
        return False
    code = error_code(raw).upper()
    return "FORBIDDEN" in code or status in (401, 403)


def table_select_denied(key: str, token: str) -> bool:
    status, raw = http(
        key,
        "GET",
        "/rest/v1/pet_care_transfers?select=id&limit=1",
        token=token,
    )
    if status == 200:
        return False
    upper = raw.upper()
    return status in (401, 403) or "PERMISSION" in upper or "42501" in upper or "DENIED" in upper


def publication_apps(key: str, token: str) -> list[dict]:
    status, raw = rpc(key, token, "canon_list_my_adoption_applications")
    rows = as_rows(status, raw, "APPLICATIONS")
    return [
        row
        for row in rows
        if isinstance(row, dict) and row.get("publication_id") == PUBLICATION_ID
    ]


def snapshot(key: str, token: str, user_id: str) -> dict:
    context = as_object(
        *rpc(key, token, "canon_get_pet_care_context", {"p_pet_id": PET_ID}),
        "CARE_CONTEXT",
    )
    holders = as_rows(
        *rpc(key, token, "canon_list_pet_holders", {"p_pet_id": PET_ID}),
        "HOLDERS",
    )
    transfers = as_rows(
        *rpc(key, token, "canon_list_care_transfers", {"p_pet_id": PET_ID}),
        "TRANSFERS",
    )
    moments = as_rows(
        *rpc(key, token, "canon_list_vitacora_moments", {"p_pet_id": PET_ID}),
        "VITACORA",
    )
    apps = publication_apps(key, token)
    if len(apps) != 1:
        raise SystemExit("APPLICATION COUNT UNEXPECTED")
    application = as_object(
        *rpc(key, token, "canon_get_adoption_application", {"p_application_id": apps[0]["id"]}),
        "APPLICATION",
    )
    chosen = [row for row in transfers if isinstance(row, dict) and row.get("id") == TRANSFER_ID]
    if len(chosen) != 1:
        raise SystemExit("TRANSFER COUNT UNEXPECTED")
    active = [row for row in holders if isinstance(row, dict) and row.get("status") == "ACTIVE"]
    return {
        "transfer": chosen[0],
        "transfer_count": len(transfers),
        "context": {
            "pet_id": context.get("pet_id"),
            "kind": context.get("current_custodian_kind"),
            "person_id": context.get("principal_person_id"),
            "organization_id": context.get("principal_organization_id"),
            "display_name": context.get("principal_display_name"),
        },
        "active_holders": sorted(
            (
                row.get("holder_kind"),
                row.get("role"),
                row.get("person_id"),
                row.get("organization_id"),
                row.get("username"),
            )
            for row in active
        ),
        "holder_count": len(holders),
        "active_holder_count": len(active),
        "application": {
            "id": application.get("id"),
            "status": application.get("status"),
            "publication_status": application.get("publication_status"),
            "applicant_user_id": application.get("applicant_user_id"),
        },
        "moments": sorted(json.dumps(row, sort_keys=True) for row in moments if isinstance(row, dict)),
        "recipient_user_id": user_id,
    }


def accept(key: str, token: str):
    return rpc(key, token, "canon_accept_care_transfer", {"p_transfer_id": TRANSFER_ID})


def main() -> int:
    refuse_non_staging()
    key = os.environ.get("SUPABASE_STAGING_PUBLISHABLE_KEY", "").strip()
    password = os.environ.get("LEOVER_QA_PASSWORD", "").strip()
    assert_publishable_key(key)
    if not key or not password:
        raise SystemExit("QA credentials missing")

    sessions = {label: login(key, password, username) for label, username in ACTORS.items()}
    qa14, qa14_id = sessions["QA14"]
    qa15, _qa15_id = sessions["QA15"]
    qa01, _qa01_id = sessions["QA01"]
    qa07, _qa07_id = sessions["QA07"]

    if not table_select_denied(key, qa14) or not table_select_denied(key, qa01):
        raise SystemExit("DIRECT TABLE SELECT LEAKED")

    before = snapshot(key, qa14, qa14_id)
    qa15_before = publication_apps(key, qa15)
    if before["transfer"].get("status") != "ACCEPTED":
        raise SystemExit("TRANSFER IS NOT ALREADY ACCEPTED")
    if before["transfer"].get("pet_id") != PET_ID:
        raise SystemExit("TRANSFER PET MISMATCH")
    if before["transfer"].get("target_person_id") != qa14_id:
        raise SystemExit("TRANSFER RECIPIENT MISMATCH")
    if before["context"]["person_id"] != qa14_id or before["context"]["kind"] != "PERSON":
        raise SystemExit("HOLDER IS NOT QA14")
    if before["application"]["status"] != "COMPLETED":
        raise SystemExit("QA14 APPLICATION IS NOT COMPLETED")
    if before["application"]["publication_status"] != "CLOSED":
        raise SystemExit("PUBLICATION IS NOT CLOSED")
    if len(qa15_before) != 1 or qa15_before[0].get("status") != "CLOSED":
        raise SystemExit("QA15 APPLICATION IS NOT CLOSED")

    qa15_status, qa15_raw = accept(key, qa15)
    qa01_status, qa01_raw = accept(key, qa01)
    qa07_status, qa07_raw = accept(key, qa07)
    if not denied(qa15_status, qa15_raw):
        raise SystemExit(f"QA15 ACCEPTED-TRANSFER CALL LEAKED http={qa15_status} {error_code(qa15_raw)}")
    if not denied(qa01_status, qa01_raw):
        raise SystemExit(f"UNRELATED ACCEPTED-TRANSFER CALL LEAKED http={qa01_status} {error_code(qa01_raw)}")
    if not denied(qa07_status, qa07_raw):
        raise SystemExit(f"QA07 INITIATOR CALL LEAKED http={qa07_status} {error_code(qa07_raw)}")

    after_denied = snapshot(key, qa14, qa14_id)
    if after_denied != before:
        raise SystemExit("DENIED CALL CHANGED FIXTURE")

    qa14_status, qa14_raw = accept(key, qa14)
    accepted = as_object(qa14_status, qa14_raw, "QA14 ACCEPT")
    if accepted.get("id") != TRANSFER_ID or accepted.get("status") != "ACCEPTED":
        raise SystemExit("QA14 RETRY STATUS UNEXPECTED")
    if accepted.get("decided_at") != before["transfer"].get("decided_at"):
        raise SystemExit("QA14 RETRY CHANGED DECIDED_AT")
    if accepted.get("created_at") != before["transfer"].get("created_at"):
        raise SystemExit("QA14 RETRY CHANGED CREATED_AT")

    after_retry = snapshot(key, qa14, qa14_id)
    qa15_after = publication_apps(key, qa15)
    if after_retry != before:
        raise SystemExit("AUTHORIZED RETRY CHANGED FIXTURE")
    if qa15_after != qa15_before:
        raise SystemExit("QA15 APPLICATION CHANGED")

    second_status, second_raw = accept(key, qa14)
    second = as_object(second_status, second_raw, "QA14 SECOND RETRY")
    if second != accepted:
        raise SystemExit("SECOND RETRY RESULT CHANGED")
    if snapshot(key, qa14, qa14_id) != before:
        raise SystemExit("SECOND RETRY CHANGED FIXTURE")

    holder = before["active_holders"][0] if before["active_holders"] else None
    print(
        json.dumps(
            {
                "direct_table_select": "DENIED",
                "qa14_retry_status": accepted.get("status"),
                "qa14_http": qa14_status,
                "qa15_call": "DENIED",
                "qa15_code": error_code(qa15_raw),
                "unrelated_call": "DENIED",
                "unrelated_code": error_code(qa01_raw),
                "qa07_call": "DENIED",
                "qa07_code": error_code(qa07_raw),
                "transfer_id": before["transfer"].get("id"),
                "transfer_status": before["transfer"].get("status"),
                "transfer_count": before["transfer_count"],
                "decided_at": before["transfer"].get("decided_at"),
                "pet_id": before["context"]["pet_id"],
                "holder_kind": before["context"]["kind"],
                "holder_person_id": before["context"]["person_id"],
                "holder_display_name": before["context"]["display_name"],
                "active_holder": holder,
                "holder_count": before["holder_count"],
                "active_holder_count": before["active_holder_count"],
                "publication_status": before["application"]["publication_status"],
                "qa14_application": before["application"]["status"],
                "qa15_application": qa15_before[0].get("status"),
                "vitacora_count": len(before["moments"]),
                "completion_hook_rerun": "NO",
                "writes": [],
            },
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
