#!/usr/bin/env python3
"""Idempotent STAGING probe for the Luna adoption lifecycle.

User sessions only:
  SUPABASE_STAGING_PUBLISHABLE_KEY
  LEOVER_QA_PASSWORD

Staging project: tobqbddfcyitwgbkthhy.
Refuses the legacy/prod ref. Does not use service_role, a database
password, or SQL.

Flow, at most once:
  QA14 applies, QA15 applies, QA07 accepts QA14, QA07 initiates one
  care transfer, QA14 accepts it.

A later run sees the terminal states and only reads. It does not create
another Luna publication, application, or transfer.

Direct legacy finalize is not called.
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
PET_NAME = "QA - Luna Adopcion"
SHELTER_ORG_ID = "8a34399d-bc11-408e-9b67-b15aa187fa5b"

ACTIVE = {"PENDING", "SUBMITTED", "IN_REVIEW", "ACCEPTED", "PAUSED"}
PROFILE_FIELDS = (
    "housing_type",
    "housing_tenure",
    "animals_allowed",
    "adults_count",
    "primary_caretaker",
    "motivation",
)

ACTORS = {
    "QA07": "qa07shelter",
    "QA14": "qa14adopter",
    "QA15": "qa15adopter2",
    "QA01": "qa01owner",
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
        for marker in (
            "NOT_AUTHENTICATED",
            "FORBIDDEN",
            "NOT_FOUND",
            "PET_TRANSFER_PENDING_EXISTS",
            "PET_TRANSFER_NOT_PENDING",
            "PERMISSION DENIED",
            "42501",
        ):
            if marker in upper or marker in code:
                return marker
        if message:
            return message[:120]
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
    email = parse_json(raw).get("email") if isinstance(parse_json(raw), dict) else None
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


def as_rows(status: int, raw: str) -> list:
    if status != 200:
        raise SystemExit(f"RPC FAIL http={status} {error_code(raw)}")
    body = parse_json(raw)
    if body is None:
        return []
    if isinstance(body, list):
        return body
    raise SystemExit("RPC FAIL unexpected list")


def as_object(status: int, raw: str) -> dict:
    if status != 200:
        raise SystemExit(f"RPC FAIL http={status} {error_code(raw)}")
    body = parse_json(raw)
    if not isinstance(body, dict):
        raise SystemExit("RPC FAIL unexpected object")
    return body


def denied(status: int, raw: str) -> bool:
    if status == 200:
        return False
    code = error_code(raw).upper()
    return (
        "FORBIDDEN" in code
        or "PERMISSION DENIED" in code
        or "42501" in code
        or "NOT_AUTHENTICATED" in code
        or status in (401, 403)
    )


def profile_ready(key: str, token: str) -> bool:
    status, raw = rpc(key, token, "canon_list_my_adoption_general_profile")
    if status != 200:
        return False
    body = parse_json(raw)
    if not isinstance(body, dict) or not body:
        return False
    return all(body.get(field) not in (None, "") for field in PROFILE_FIELDS)


def my_apps(key: str, token: str) -> list[dict]:
    status, raw = rpc(key, token, "canon_list_my_adoption_applications")
    return [row for row in as_rows(status, raw) if isinstance(row, dict)]


def managed_apps(key: str, token: str) -> list[dict]:
    status, raw = rpc(
        key,
        token,
        "canon_list_adoption_applications",
        {"p_publication_id": PUBLICATION_ID},
    )
    return [row for row in as_rows(status, raw) if isinstance(row, dict)]


def one_app(key: str, token: str, application_id: str) -> dict:
    status, raw = rpc(
        key,
        token,
        "canon_get_adoption_application",
        {"p_application_id": application_id},
    )
    return as_object(status, raw)


def for_publication(rows: list[dict], user_id: str | None = None) -> list[dict]:
    chosen = []
    for row in rows:
        if row.get("publication_id") != PUBLICATION_ID:
            continue
        if user_id and row.get("applicant_user_id") != user_id:
            continue
        chosen.append(row)
    return chosen


def active_rows(rows: list[dict]) -> list[dict]:
    return [row for row in rows if row.get("status") in ACTIVE]


def open_luna(key: str, token: str) -> dict | None:
    status, raw = rpc(key, token, "canon_list_adoptions")
    for row in as_rows(status, raw):
        if isinstance(row, dict) and row.get("id") == PUBLICATION_ID:
            return row
    return None


def care_context(key: str, token: str) -> dict:
    status, raw = rpc(key, token, "canon_get_pet_care_context", {"p_pet_id": PET_ID})
    return as_object(status, raw)


def holders(key: str, token: str) -> list[dict]:
    status, raw = rpc(key, token, "canon_list_pet_holders", {"p_pet_id": PET_ID})
    return [row for row in as_rows(status, raw) if isinstance(row, dict)]


def transfers(key: str, token: str) -> list[dict]:
    status, raw = rpc(key, token, "canon_list_care_transfers", {"p_pet_id": PET_ID})
    return [row for row in as_rows(status, raw) if isinstance(row, dict)]


def moments(key: str, token: str) -> list[dict]:
    status, raw = rpc(key, token, "canon_list_vitacora_moments", {"p_pet_id": PET_ID})
    if status != 200:
        return []
    body = parse_json(raw)
    if not isinstance(body, list):
        return []
    return [row for row in body if isinstance(row, dict)]


def holder_signature(context: dict, links: list[dict]) -> dict:
    active = [row for row in links if row.get("status") == "ACTIVE"]
    return {
        "pet_id": context.get("pet_id"),
        "pet_name": context.get("pet_name"),
        "kind": context.get("current_custodian_kind"),
        "person_id": context.get("principal_person_id"),
        "organization_id": context.get("principal_organization_id"),
        "display_name": context.get("principal_display_name"),
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
    }


def same_holder(before: dict, after: dict) -> bool:
    return before == after


def table_select_denied(key: str, token: str | None) -> bool:
    status, raw = http(
        key,
        "GET",
        "/rest/v1/adoption_applications?select=id&limit=1",
        token=token,
    )
    return denied(status, raw) and "adoption_applications" in raw


def function_exists(key: str, token: str, name: str, body: dict) -> bool:
    status, raw = rpc(key, token, name, body)
    if status == 200:
        return True
    code = error_code(raw).upper()
    if "PGRST202" in code or "COULD NOT FIND THE FUNCTION" in code:
        return False
    return status in (400, 401, 403, 404) or "NOT_FOUND" in code or "FORBIDDEN" in code


def apply_once(key: str, token: str) -> tuple[str, str]:
    status, raw = rpc(key, token, "canon_apply_adoption", {"p_publication_id": PUBLICATION_ID})
    if status != 200:
        raise SystemExit(f"APPLY FAIL http={status} {error_code(raw)}")
    application_id = parse_json(raw)
    if not isinstance(application_id, str) or len(application_id) < 32:
        raise SystemExit("APPLY FAIL missing id")
    row = one_app(key, token, application_id)
    if row.get("id") != application_id or row.get("publication_id") != PUBLICATION_ID:
        raise SystemExit("APPLY FAIL unreadable application")
    if row.get("pet_id") != PET_ID:
        raise SystemExit("APPLY FAIL pet mismatch")
    return application_id, str(row.get("status"))


def ensure_application(key: str, token: str, user_id: str) -> tuple[str, str, str]:
    existing = active_rows(for_publication(my_apps(key, token), user_id))
    if len(existing) > 1:
        raise SystemExit("REFUSING DUPLICATE ACTIVE APPLICATION")
    created = "REUSED" if existing else "CREATED"
    if existing:
        application_id = existing[0]["id"]
        status_name = str(existing[0].get("status"))
    else:
        application_id, status_name = apply_once(key, token)
    retry_status, retry_raw = rpc(
        key, token, "canon_apply_adoption", {"p_publication_id": PUBLICATION_ID}
    )
    if retry_status != 200:
        raise SystemExit(f"APPLY RETRY FAIL http={retry_status} {error_code(retry_raw)}")
    retry_id = parse_json(retry_raw)
    if retry_id != application_id:
        raise SystemExit("APPLY RETRY DUPLICATED")
    again = active_rows(for_publication(my_apps(key, token), user_id))
    if len(again) != 1 or again[0].get("id") != application_id:
        raise SystemExit("REFUSING DUPLICATE ACTIVE APPLICATION")
    return application_id, status_name, created


def initiate_transfer(key: str, token: str, target_user_id: str) -> dict:
    body = {
        "p_pet_id": PET_ID,
        "p_target_kind": "PERSON",
        "p_target_person_id": target_user_id,
        "p_target_organization_id": None,
        "p_share_personal_media": False,
    }
    status, raw = rpc(key, token, "canon_initiate_care_transfer", body)
    if status == 200:
        row = as_object(status, raw)
    elif error_code(raw) == "PET_TRANSFER_PENDING_EXISTS":
        pending = [
            row
            for row in transfers(key, token)
            if row.get("status") == "PENDING" and row.get("pet_id") == PET_ID
        ]
        if len(pending) != 1:
            raise SystemExit("TRANSFER PENDING COUNT UNEXPECTED")
        row = pending[0]
    else:
        raise SystemExit(f"TRANSFER INITIATE FAIL http={status} {error_code(raw)}")
    status, raw = rpc(key, token, "canon_initiate_care_transfer", body)
    if error_code(raw) != "PET_TRANSFER_PENDING_EXISTS" and status == 200:
        duplicate = as_object(status, raw)
        if duplicate.get("id") != row.get("id"):
            raise SystemExit("TRANSFER RETRY DUPLICATED")
    elif error_code(raw) != "PET_TRANSFER_PENDING_EXISTS":
        raise SystemExit(f"TRANSFER RETRY FAIL http={status} {error_code(raw)}")
    pending = [item for item in transfers(key, token) if item.get("status") == "PENDING"]
    if len(pending) != 1 or pending[0].get("id") != row.get("id"):
        raise SystemExit("TRANSFER RETRY DUPLICATED")
    if row.get("target_person_id") != target_user_id or row.get("target_kind") != "PERSON":
        raise SystemExit("TRANSFER TARGET MISMATCH")
    if row.get("status") != "PENDING":
        raise SystemExit("TRANSFER STATUS NOT PENDING")
    return row


def main() -> int:
    refuse_non_staging()
    read_only = "--read-only" in sys.argv
    key = os.environ.get("SUPABASE_STAGING_PUBLISHABLE_KEY", "").strip()
    password = os.environ.get("LEOVER_QA_PASSWORD", "").strip()
    assert_publishable_key(key)
    if not key or not password:
        raise SystemExit("QA credentials missing")

    sessions = {}
    for label, username in ACTORS.items():
        sessions[label] = login(key, password, username)
    qa07, qa07_id = sessions["QA07"]
    qa14, qa14_id = sessions["QA14"]
    qa15, qa15_id = sessions["QA15"]
    qa01, _qa01_id = sessions["QA01"]

    report = {
        "mode": "READ",
        "direct_select": "DENIED" if table_select_denied(key, qa14) and table_select_denied(key, None) else "LEAKED",
        "functions": {},
        "writes": [],
    }
    missing_function = False
    existence = {
        "canon_list_my_adoption_applications": {},
        "canon_list_adoption_applications": {"p_publication_id": PUBLICATION_ID},
        "canon_get_adoption_application": {"p_application_id": "00000000-0000-0000-0000-000000000001"},
        "canon_apply_adoption": {"p_publication_id": "00000000-0000-0000-0000-000000000001"},
        "canon_accept_adoption_application": {"p_application_id": "00000000-0000-0000-0000-000000000001"},
        "canon_initiate_care_transfer": {
            "p_pet_id": "00000000-0000-0000-0000-000000000001",
            "p_target_kind": "PERSON",
            "p_target_person_id": "00000000-0000-0000-0000-000000000002",
            "p_target_organization_id": None,
            "p_share_personal_media": False,
        },
        "canon_accept_care_transfer": {"p_transfer_id": "00000000-0000-0000-0000-000000000001"},
    }
    for name, body in existence.items():
        present = function_exists(key, qa07, name, body)
        report["functions"][name] = present
        missing_function = missing_function or not present
    if missing_function or report["direct_select"] != "DENIED":
        print(json.dumps(report, indent=2))
        return 1

    if not profile_ready(key, qa14) or not profile_ready(key, qa15):
        raise SystemExit("ADOPTION PROFILE MISSING")

    publication = open_luna(key, qa07)
    initial_publication = publication.get("status") if publication else "NOT_OPEN"
    qa14_existing = for_publication(my_apps(key, qa14), qa14_id)
    qa15_existing = for_publication(my_apps(key, qa15), qa15_id)
    qa14_active = active_rows(qa14_existing)
    qa15_active = active_rows(qa15_existing)
    completed = publication is None and any(row.get("status") == "COMPLETED" for row in qa14_existing)
    if completed:
        history = transfers(key, qa14)
    else:
        history = []
    accepted_transfer = next(
        (
            row
            for row in history
            if row.get("status") == "ACCEPTED"
            and row.get("pet_id") == PET_ID
            and row.get("target_person_id") == qa14_id
        ),
        None,
    )
    terminal = (
        completed
        and accepted_transfer is not None
        and any(row.get("status") == "CLOSED" for row in qa15_existing)
        and not qa14_active
        and not qa15_active
    )
    if read_only and not terminal:
        raise SystemExit("READ ONLY REQUESTED BEFORE COMPLETION")
    if terminal:
        final_context = care_context(key, qa14)
        final_holder = holder_signature(final_context, holders(key, qa14))
        qa14_final = next(row for row in qa14_existing if row.get("status") == "COMPLETED")
        qa15_final = next(row for row in qa15_existing if row.get("status") == "CLOSED")
        publication_status = one_app(key, qa14, qa14_final["id"]).get("publication_status")
        print("MODE READ")
        print("DIRECT_FINALIZE_NOT_CALLED")
        print(
            json.dumps(
                {
                    "direct_select": report["direct_select"],
                    "qa14_id": qa14_final.get("id"),
                    "qa14_status": qa14_final.get("status"),
                    "qa15_id": qa15_final.get("id"),
                    "qa15_status": qa15_final.get("status"),
                    "publication_status": publication_status,
                    "transfer_id": accepted_transfer.get("id"),
                    "transfer_status": accepted_transfer.get("status"),
                    "pet_id": final_context.get("pet_id"),
                    "holder": final_holder,
                    "writes": [],
                },
                indent=2,
            )
        )
        return 0

    if initial_publication != "OPEN":
        raise SystemExit("LUNA PUBLICATION NOT OPEN")
    if publication.get("pet_id") != PET_ID or publication.get("published_by") != qa07_id:
        raise SystemExit("LUNA PUBLISHER MISMATCH")
    if publication.get("organization_id") != SHELTER_ORG_ID:
        raise SystemExit("LUNA ORGANIZATION MISMATCH")
    if len([row for row in as_rows(*rpc(key, qa07, "canon_list_adoptions")) if row.get("pet_id") == PET_ID]) != 1:
        raise SystemExit("REFUSING EXTRA LUNA PUBLICATION")
    context = care_context(key, qa07)
    if context.get("pet_id") != PET_ID or context.get("pet_name") != PET_NAME:
        raise SystemExit("LUNA FIXTURE MISMATCH")
    if context.get("can_initiate_transfer") is not True:
        print("STOP can_initiate_transfer false")
        print(json.dumps({"can_initiate_transfer": context.get("can_initiate_transfer")}, indent=2))
        return 3
    before_holder = holder_signature(context, holders(key, qa07))
    before_moments = {row.get("id") for row in moments(key, qa07)}

    report["mode"] = "MUTATE"
    qa14_id_app, qa14_initial, qa14_how = ensure_application(key, qa14, qa14_id)
    qa15_id_app, qa15_initial, qa15_how = ensure_application(key, qa15, qa15_id)
    if qa14_how == "CREATED":
        report["writes"].append("qa14_apply")
    if qa15_how == "CREATED":
        report["writes"].append("qa15_apply")
    qa14_row = one_app(key, qa14, qa14_id_app)
    qa15_row = one_app(key, qa15, qa15_id_app)
    if qa14_row.get("applicant_user_id") != qa14_id or qa15_row.get("applicant_user_id") != qa15_id:
        raise SystemExit("APPLICANT IDENTITY MISMATCH")

    visible = managed_apps(key, qa07)
    visible_ids = {row.get("id") for row in visible}
    if qa14_id_app not in visible_ids or qa15_id_app not in visible_ids:
        raise SystemExit("QA07 DOES NOT SEE BOTH")

    unrelated_status, unrelated_raw = rpc(
        key, qa01, "canon_list_adoption_applications", {"p_publication_id": PUBLICATION_ID}
    )
    qa14_manage_status, qa14_manage_raw = rpc(
        key, qa14, "canon_get_adoption_application", {"p_application_id": qa15_id_app}
    )
    qa15_accept_status, qa15_accept_raw = rpc(
        key, qa15, "canon_accept_adoption_application", {"p_application_id": qa14_id_app}
    )
    if not denied(unrelated_status, unrelated_raw):
        raise SystemExit("UNRELATED APPLICATION READ LEAKED")
    if not denied(qa14_manage_status, qa14_manage_raw):
        raise SystemExit("QA14 MANAGED QA15")
    if not denied(qa15_accept_status, qa15_accept_raw):
        raise SystemExit("QA15 ACCEPTED PUBLICATION")
    if one_app(key, qa14, qa14_id_app).get("status") != qa14_row.get("status"):
        raise SystemExit("QA15 ACCEPT MUTATED QA14")

    qa14_now = one_app(key, qa07, qa14_id_app)
    qa15_now = one_app(key, qa07, qa15_id_app)
    if qa14_now.get("status") != "ACCEPTED":
        status, raw = rpc(
            key, qa07, "canon_accept_adoption_application", {"p_application_id": qa14_id_app}
        )
        if status != 200 or parse_json(raw) is not True:
            raise SystemExit(f"ACCEPT APPLICATION FAIL http={status} {error_code(raw)}")
        report["writes"].append("qa07_accept_qa14")
        qa14_now = one_app(key, qa07, qa14_id_app)
        qa15_now = one_app(key, qa07, qa15_id_app)
    publication_after = open_luna(key, qa07)
    context_after = care_context(key, qa07)
    holder_after_selection = holder_signature(context_after, holders(key, qa07))
    if qa14_now.get("status") != "ACCEPTED" or qa15_now.get("status") != "PAUSED":
        raise SystemExit("SELECTION STATUS UNEXPECTED")
    if publication_after is None or publication_after.get("status") != "OPEN":
        raise SystemExit("PUBLICATION CLOSED AT SELECTION")
    if not same_holder(before_holder, holder_after_selection):
        raise SystemExit("HOLDER CHANGED AT SELECTION")
    if any(row.get("status") == "ACCEPTED" for row in transfers(key, qa07)):
        raise SystemExit("TRANSFER ACCEPTED DURING SELECTION")

    print("DIRECT_FINALIZE_NOT_CALLED")
    pending = next(
        (row for row in transfers(key, qa07) if row.get("status") == "PENDING"),
        None,
    )
    if pending is None:
        pending = initiate_transfer(key, qa07, qa14_id)
        report["writes"].append("qa07_initiate_transfer")
    elif pending.get("target_person_id") != qa14_id:
        raise SystemExit("PENDING TRANSFER TARGET MISMATCH")
    else:
        initiate_transfer(key, qa07, qa14_id)
    context_pending = care_context(key, qa07)
    holder_pending = holder_signature(context_pending, holders(key, qa07))
    publication_pending = open_luna(key, qa07)
    qa14_pending = one_app(key, qa07, qa14_id_app)
    qa15_pending = one_app(key, qa07, qa15_id_app)
    if not same_holder(before_holder, holder_pending):
        raise SystemExit("HOLDER CHANGED BEFORE TRANSFER ACCEPT")
    if publication_pending is None or publication_pending.get("status") != "OPEN":
        raise SystemExit("PUBLICATION CLOSED BEFORE TRANSFER ACCEPT")
    if qa14_pending.get("status") != "ACCEPTED" or qa15_pending.get("status") != "PAUSED":
        raise SystemExit("APPLICATION STATUS CHANGED BEFORE TRANSFER ACCEPT")
    if context_pending.get("principal_person_id") not in (None, ""):
        raise SystemExit("QA14 BECAME HOLDER BEFORE TRANSFER ACCEPT")

    qa15_transfer_status, qa15_transfer_raw = rpc(
        key, qa15, "canon_accept_care_transfer", {"p_transfer_id": pending["id"]}
    )
    qa01_transfer_status, qa01_transfer_raw = rpc(
        key, qa01, "canon_accept_care_transfer", {"p_transfer_id": pending["id"]}
    )
    if not denied(qa15_transfer_status, qa15_transfer_raw):
        raise SystemExit("QA15 TRANSFER ACCEPT LEAKED")
    if not denied(qa01_transfer_status, qa01_transfer_raw):
        raise SystemExit("UNRELATED TRANSFER ACCEPT LEAKED")
    still = next(row for row in transfers(key, qa07) if row.get("id") == pending["id"])
    if still.get("status") != "PENDING":
        raise SystemExit("DENIED ACCEPT CHANGED TRANSFER")

    status, raw = rpc(key, qa14, "canon_accept_care_transfer", {"p_transfer_id": pending["id"]})
    if status != 200:
        raise SystemExit(f"QA14 TRANSFER ACCEPT FAIL http={status} {error_code(raw)}")
    accepted = as_object(status, raw)
    report["writes"].append("qa14_accept_transfer")
    if accepted.get("status") != "ACCEPTED" or accepted.get("pet_id") != PET_ID:
        raise SystemExit("TRANSFER FINAL STATUS UNEXPECTED")

    qa14_final = one_app(key, qa14, qa14_id_app)
    qa15_final = one_app(key, qa15, qa15_id_app)
    final_context = care_context(key, qa14)
    final_holder = holder_signature(final_context, holders(key, qa14))
    final_moments = moments(key, qa14)
    created_anchor = next(
        (row for row in final_moments if row.get("kind") == "CARE_CREATED" and row.get("id") == PET_ID),
        None,
    )
    if qa14_final.get("status") != "COMPLETED" or qa15_final.get("status") != "CLOSED":
        raise SystemExit("APPLICATION FINAL STATUS UNEXPECTED")
    if qa14_final.get("publication_status") != "CLOSED" or qa15_final.get("publication_status") != "CLOSED":
        raise SystemExit("PUBLICATION FINAL STATUS UNEXPECTED")
    if open_luna(key, qa14) is not None or open_luna(key, qa07) is not None:
        raise SystemExit("PUBLICATION STILL OPEN")
    if final_context.get("pet_id") != PET_ID or final_context.get("pet_name") != PET_NAME:
        raise SystemExit("PET IDENTITY CHANGED")
    if final_context.get("principal_person_id") != qa14_id or final_context.get("current_custodian_kind") != "PERSON":
        raise SystemExit("FINAL HOLDER UNEXPECTED")
    if final_context.get("principal_organization_id") not in (None, ""):
        raise SystemExit("ORGANIZATION STILL CUSTODIAN")
    if created_anchor is None:
        raise SystemExit("VITACORA ANCHOR MISSING")
    if before_moments and not before_moments.issubset({row.get("id") for row in final_moments}):
        raise SystemExit("VITACORA HISTORY DROPPED")

    print("MODE MUTATE")
    print("DIRECT_FINALIZE_NOT_CALLED")
    print(
        json.dumps(
            {
                "direct_select": report["direct_select"],
                "initial_publication": initial_publication,
                "qa14_id": qa14_id_app,
                "qa14_initial_status": qa14_initial,
                "qa14_how": qa14_how,
                "qa14_retry": "SAME_ID",
                "qa14_applicant": qa14_row.get("applicant_user_id"),
                "qa15_id": qa15_id_app,
                "qa15_initial_status": qa15_initial,
                "qa15_how": qa15_how,
                "qa07_sees_both": True,
                "unrelated_application_read": "DENIED",
                "qa14_after_selection": qa14_now.get("status"),
                "qa15_after_selection": qa15_now.get("status"),
                "publication_after_selection": publication_after.get("status"),
                "holder_after_selection": holder_after_selection,
                "holder_before_transfer_accept": holder_pending,
                "ownership_changed_before_accept": False,
                "transfer_id": accepted.get("id"),
                "transfer_pending_status": pending.get("status"),
                "qa15_transfer_accept": "DENIED",
                "transfer_final_status": accepted.get("status"),
                "qa14_final": qa14_final.get("status"),
                "qa15_final": qa15_final.get("status"),
                "publication_final": qa14_final.get("publication_status"),
                "pet_id_after": final_context.get("pet_id"),
                "final_holder": final_holder,
                "care_created_id": created_anchor.get("id"),
                "writes": report["writes"],
            },
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
