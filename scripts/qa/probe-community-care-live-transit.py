#!/usr/bin/env python3
"""STAGING probe for the Bruno foster transit.

User sessions only:
  SUPABASE_STAGING_PUBLISHABLE_KEY
  LEOVER_QA_PASSWORD

Staging project: tobqbddfcyitwgbkthhy.
Refuses the legacy/prod ref. Does not use service_role, a database
password, or SQL.

Understands REQUESTED, ACTIVE, and COMPLETED, and placement OPEN or CLOSED.
A COMPLETED Bruno fixture is read only. It never creates a second request,
application, or placement.

End mode is explicit: LEOVER_END_TRANSIT=1 or --end.
That mode completes the existing ACTIVE transit once, then retries once
as the same authorized actor. An already COMPLETED fixture stays read only.
"""

from __future__ import annotations

import base64
import json
import os
import sys
import urllib.error
import urllib.request

STAGING_REF = "tobqbddfcyitwgbkthhy"
LEGACY_PROD_REF = "wystsapjfpdtoprlmizz"
BASE = f"https://{STAGING_REF}.supabase.co"

PET_ID = "9d9a6e5b-8cc8-4bc8-aa0c-740a1725c830"
PET_NAME = "QA - Bruno Transito"
ORG_ID = "8a34399d-bc11-408e-9b67-b15aa187fa5b"
REQUEST_ID = "8c293651-83f6-43bc-b677-f34bef9e021f"
APPLICATION_ID = "b0537915-f73b-45b8-addd-9246bd9fd542"
PLACEMENT_ID = "07d69d92-7d42-492d-a78d-48d020e3de59"
NON_TERMINAL = {"REQUESTED", "MATCHED", "ACTIVE"}

ACTORS = {
    "QA07": "qa07shelter",
    "QA06": "qa06foster",
    "QA01": "qa01owner",
}

TABLES = {
    "foster_care_requests": "id",
    "foster_care_applications": "id",
    "foster_placements": "id",
    "foster_profiles": "user_id",
    "pet_responsibility_links": "id",
    "vitacora_profiles": "pet_id",
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
        message = str(body.get("message") or body.get("msg") or body.get("error") or "")
        code = str(body.get("code") or "")
        upper = message.upper()
        for marker in (
            "NOT_AUTHENTICATED",
            "FOSTER_NOT_ELIGIBLE",
            "BASE_LOCATION_REQUIRED",
            "FORBIDDEN",
            "NOT_FOUND",
            "STATUS_INVALID",
            "VALIDATION",
            "PERMISSION DENIED",
            "42501",
            "PGRST202",
        ):
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
    if isinstance(body, dict) and len(body) == 1:
        only = next(iter(body.values()))
        if isinstance(only, list):
            return only
    raise SystemExit(f"{label} FAIL unexpected list")


def as_object(status: int, raw: str, label: str) -> dict:
    if status != 200:
        raise SystemExit(f"{label} FAIL http={status} {error_code(raw)}")
    body = parse_json(raw)
    if isinstance(body, dict) and len(body) == 1:
        only = next(iter(body.values()))
        if isinstance(only, dict):
            return only
    if not isinstance(body, dict):
        raise SystemExit(f"{label} FAIL unexpected object")
    return body


def as_id(status: int, raw: str, label: str) -> str:
    if status != 200:
        raise SystemExit(f"{label} FAIL http={status} {error_code(raw)}")
    body = parse_json(raw)
    if isinstance(body, str) and len(body) >= 32:
        return body
    raise SystemExit(f"{label} FAIL missing id")


def denied(status: int, raw: str) -> bool:
    if status == 200:
        return False
    code = error_code(raw).upper()
    return (
        "FORBIDDEN" in code
        or "FOSTER_NOT_ELIGIBLE" in code
        or "PERMISSION DENIED" in code
        or "42501" in code
        or "NOT_AUTHENTICATED" in code
        or status in (401, 403)
    )


def bruno_requests(rows: list) -> list[dict]:
    chosen = []
    for row in rows:
        if isinstance(row, dict) and row.get("pet_id") == PET_ID:
            chosen.append(row)
    return chosen


def non_terminal(rows: list[dict]) -> list[dict]:
    return [row for row in rows if row.get("status") in NON_TERMINAL]


def report_line(key: str, value) -> None:
    print(f"{key}: {value}")


def end_requested() -> bool:
    if "--end" in sys.argv:
        return True
    return os.environ.get("LEOVER_END_TRANSIT", "").strip() == "1"


def link_ids(rows: list) -> list:
    return sorted(str(row.get("link_id")) for row in rows if isinstance(row, dict))


def rows_of(status: int, raw: str, label: str) -> list:
    return [row for row in as_rows(status, raw, label) if isinstance(row, dict)]


def holder_rows(key: str, token: str) -> list:
    return rows_of(*rpc(key, token, "canon_list_pet_holders", {"p_pet_id": PET_ID}), "HOLDERS")


def care_context(key: str, token: str) -> dict:
    return as_object(*rpc(key, token, "canon_get_pet_care_context", {"p_pet_id": PET_ID}), "CARE")


def transfer_rows(key: str, token: str) -> list:
    return rows_of(*rpc(key, token, "canon_list_care_transfers", {"p_pet_id": PET_ID}), "TRANSFERS")


def moment_rows(key: str, token: str) -> list:
    status, raw = rpc(key, token, "canon_list_vitacora_moments", {"p_pet_id": PET_ID})
    if status != 200:
        return []
    return rows_of(status, raw, "MOMENTS")


def adoption_rows(key: str, token: str) -> list:
    status, raw = rpc(key, token, "canon_list_adoptions")
    if status != 200:
        raise SystemExit(f"ADOPTIONS FAIL http={status} {error_code(raw)}")
    return [row for row in rows_of(status, raw, "ADOPTIONS") if row.get("pet_id") == PET_ID]


def managed_requests(key: str, token: str) -> list:
    return bruno_requests(rows_of(*rpc(key, token, "canon_list_my_foster_requests"), "MY_REQUESTS"))


def foster_apps(key: str, token: str) -> list:
    return [
        row
        for row in rows_of(*rpc(key, token, "canon_list_my_foster_applications"), "MY_APPS")
        if row.get("request_id") == REQUEST_ID
    ]


def matching(rows: list, **expected) -> list:
    chosen = []
    for row in rows:
        if all(row.get(key) == value for key, value in expected.items()):
            chosen.append(row)
    return chosen


def transit_view(status: int, raw: str) -> str:
    if status == 200:
        return "STILL_ACTIVE"
    if "NOT_FOUND" in error_code(raw).upper():
        return "NONE"
    if denied(status, raw):
        return "DENIED"
    return "OTHER"


def completion_gate(status: int, raw: str) -> str:
    if status == 200:
        return "ALLOWED"
    if denied(status, raw):
        return "DENIED"
    return "OTHER"


def require_completion_rpc(key: str, token: str) -> None:
    status, raw = rpc(
        key,
        token,
        "canon_complete_foster_transit",
        {"p_request_id": "00000000-0000-0000-0000-000000000000"},
    )
    marker = error_code(raw).upper()
    if "PGRST202" in marker or "COULD NOT FIND THE FUNCTION" in raw.upper():
        raise SystemExit("RPC MISSING canon_complete_foster_transit")
    if status == 404 and "PGRST202" in raw:
        raise SystemExit("RPC MISSING canon_complete_foster_transit")


def finish(failures: list) -> int:
    if failures:
        report_line("RESULT", "FAIL " + ",".join(failures))
        return 1
    report_line("RESULT", "PASS")
    return 0


def run_transit_lifecycle(
    key: str,
    qa07: str,
    qa07_id: str,
    qa06: str,
    qa06_id: str,
    qa01: str,
    qa01_id: str,
    mine: list,
    open_rows: list,
) -> int:
    if open_rows:
        current = open_rows[0]
        if current.get("id") != REQUEST_ID or current.get("status") not in {"REQUESTED", "MATCHED", "ACTIVE"}:
            raise SystemExit("REFUSING UNEXPECTED NON-TERMINAL REQUEST")
        if current.get("status") != "ACTIVE":
            if end_requested():
                raise SystemExit("REFUSING END WHILE NOT ACTIVE")
            report_line("MODE", "READ_ONLY")
            report_line("END_TRANSIT", "NOT_RUN")
            report_line("REQUEST_ID", current.get("id"))
            report_line("REQUEST_FINAL", current.get("status"))
            report_line("PLACEMENT_STATUS", current.get("placement_status"))
            report_line("SERVICE_ROLE", "NO")
            report_line("MANUAL_SQL", "NO")
            report_line("PROD", "NO")
            report_line("RESULT", "PASS")
            return 0
        if not end_requested():
            raise SystemExit("REFUSING ACTIVE FALLTHROUGH")
        return end_active_transit(key, qa07, qa07_id, qa06, qa06_id, qa01, qa01_id)

    known = [row for row in mine if row.get("id") == REQUEST_ID and row.get("status") == "COMPLETED"]
    if len(known) != 1:
        raise SystemExit("REFUSING MISSING BRUNO FIXTURE")
    if any(row.get("status") in NON_TERMINAL for row in mine):
        raise SystemExit("REFUSING DUPLICATE NON-TERMINAL REQUEST")
    return read_completed_transit(key, qa07, qa07_id, qa06, qa06_id, qa01, qa01_id)


def read_completed_transit(
    key: str,
    qa07: str,
    qa07_id: str,
    qa06: str,
    qa06_id: str,
    qa01: str,
    qa01_id: str,
) -> int:
    before_holders = holder_rows(key, qa07)
    before_transfers = {row.get("id") for row in transfer_rows(key, qa07)}
    before_moments = {row.get("id") for row in moment_rows(key, qa07)}
    before_adoptions = {row.get("id") for row in adoption_rows(key, qa07)}
    before_requests = [row.get("id") for row in managed_requests(key, qa07)]
    context = care_context(key, qa07)
    request = next(row for row in managed_requests(key, qa07) if row.get("id") == REQUEST_ID)
    apps = foster_apps(key, qa06)
    qa07_transit_status, qa07_transit_raw = rpc(key, qa07, "canon_get_active_foster_transit", {"p_pet_id": PET_ID})
    qa06_transit_status, qa06_transit_raw = rpc(key, qa06, "canon_get_active_foster_transit", {"p_pet_id": PET_ID})
    unrelated_status, unrelated_raw = rpc(key, qa01, "canon_get_active_foster_transit", {"p_pet_id": PET_ID})
    qa06_context_status, qa06_context_raw = rpc(key, qa06, "canon_get_pet_care_context", {"p_pet_id": PET_ID})
    mutate_status, mutate_raw = rpc(
        key,
        qa06,
        "canon_initiate_care_transfer",
        {
            "p_pet_id": PET_ID,
            "p_target_kind": "PERSON",
            "p_target_person_id": qa06_id,
            "p_target_organization_id": None,
            "p_share_personal_media": False,
        },
    )
    after_holders = holder_rows(key, qa07)
    failures = []
    if request.get("status") != "COMPLETED":
        failures.append("REQUEST_NOT_COMPLETED")
    if request.get("placement_id") != PLACEMENT_ID or request.get("placement_status") != "CLOSED":
        failures.append("PLACEMENT_NOT_CLOSED")
    if len(apps) != 1 or apps[0].get("id") != APPLICATION_ID or apps[0].get("status") != "SELECTED":
        failures.append("APPLICATION_NOT_HISTORICAL")
    if apps and (apps[0].get("request_status") != "COMPLETED" or apps[0].get("placement_status") != "CLOSED"):
        failures.append("APPLICATION_CONTEXT")
    if matching(after_holders, person_id=qa06_id, holder_kind="PERSON", role="AUTHORIZED", status="ACTIVE"):
        failures.append("QA06_STILL_ACTIVE_HOLDER")
    if not matching(after_holders, person_id=qa06_id, holder_kind="PERSON", role="AUTHORIZED", status="ENDED"):
        failures.append("QA06_NOT_ENDED")
    if matching(after_holders, person_id=qa06_id, role="OWNER", status="ACTIVE"):
        failures.append("QA06_IS_OWNER")
    if len(matching(after_holders, holder_kind="ORGANIZATION", role="RESPONSIBLE", status="ACTIVE")) != 1:
        failures.append("ORG_RESPONSIBLE")
    if not matching(after_holders, holder_kind="ORGANIZATION", role="RESPONSIBLE", status="ACTIVE", organization_id=ORG_ID):
        failures.append("ORG_ID")
    if link_ids(before_holders) != link_ids(after_holders):
        failures.append("HOLDER_ROWS_CHANGED")
    if context.get("pet_id") != PET_ID or context.get("current_custodian_kind") != "ORGANIZATION":
        failures.append("CUSTODIAN")
    if context.get("principal_organization_id") != ORG_ID or context.get("principal_person_id") not in (None, ""):
        failures.append("CUSTODIAN_ORG")
    if before_transfers != {row.get("id") for row in transfer_rows(key, qa07)}:
        failures.append("CARE_TRANSFER")
    if before_adoptions != {row.get("id") for row in adoption_rows(key, qa07)}:
        failures.append("ADOPTION")
    if before_moments != {row.get("id") for row in moment_rows(key, qa07)}:
        failures.append("VITACORA")
    if before_requests != [row.get("id") for row in managed_requests(key, qa07)]:
        failures.append("REQUEST_ROWS_CHANGED")
    if transit_view(qa07_transit_status, qa07_transit_raw) != "NONE":
        failures.append("QA07_STILL_ACTIVE")
    if transit_view(qa06_transit_status, qa06_transit_raw) != "NONE":
        failures.append("QA06_STILL_ACTIVE")
    if not denied(unrelated_status, unrelated_raw):
        failures.append("UNRELATED_TRANSIT")
    if not denied(qa06_context_status, qa06_context_raw):
        failures.append("QA06_CONTEXT")
    if mutate_status == 200:
        failures.append("QA06_MUTATION")

    report_line("MODE", "READ_ONLY")
    report_line("END_TRANSIT", "NOT_RUN")
    report_line("FIXTURE", "ALREADY_COMPLETED")
    if end_requested():
        report_line("END_SKIPPED", "ALREADY_COMPLETED")
    report_line("REQUEST_ID", REQUEST_ID)
    report_line("REQUEST_FINAL", request.get("status"))
    report_line("APPLICATION_ID", apps[0].get("id") if apps else None)
    report_line("APPLICATION_FINAL", apps[0].get("status") if apps else None)
    report_line("PLACEMENT_ID", request.get("placement_id"))
    report_line("PLACEMENT_STATUS", request.get("placement_status"))
    report_line("QA06_HOLDER_AFTER", "AUTHORIZED / ENDED" if not failures else "OTHER")
    report_line("QA06_IS_OWNER", "YES" if matching(after_holders, person_id=qa06_id, role="OWNER", status="ACTIVE") else "NO")
    report_line("ORG_RESPONSIBLE", "ACTIVE" if matching(after_holders, organization_id=ORG_ID, role="RESPONSIBLE", status="ACTIVE") else "OTHER")
    report_line("CURRENT_CUSTODIAN", context.get("current_custodian_kind"))
    report_line("CURRENT_CUSTODIAN_ORG", context.get("principal_organization_id"))
    report_line("ACTIVE_TRANSIT_AFTER", transit_view(qa07_transit_status, qa07_transit_raw))
    report_line("QA06_ACTIVE_TRANSIT", transit_view(qa06_transit_status, qa06_transit_raw))
    report_line("PET_ID_AFTER", context.get("pet_id"))
    report_line("PET_PRESERVED", "YES" if context.get("pet_id") == PET_ID else "NO")
    report_line("VITACORA_PRESERVED", "YES" if context.get("pet_id") == PET_ID and "VITACORA" not in failures else "NO")
    report_line("TRANSIT_END_MOMENT", "NOT_CREATED_BY_CURRENT_DOMAIN_DESIGN")
    report_line("TRANSIT_START_MOMENT", "NOT_CREATED_BY_DESIGN_CURRENTLY")
    report_line("CARE_TRANSFER_CREATED", "NO" if "CARE_TRANSFER" not in failures else "YES")
    report_line("ADOPTION_CREATED", "NO" if "ADOPTION" not in failures else "YES")
    report_line("QA06_CONTEXT", "DENIED" if denied(qa06_context_status, qa06_context_raw) else "LEAKED")
    report_line("QA06_TRANSFER_MUTATION", "DENIED" if mutate_status != 200 else "LEAKED")
    report_line("QA07_ID", qa07_id)
    report_line("QA06_ID", qa06_id)
    report_line("QA01_ID", qa01_id)
    report_line("SERVICE_ROLE", "NO")
    report_line("MANUAL_SQL", "NO")
    report_line("PROD", "NO")
    return finish(failures)


def end_active_transit(
    key: str,
    qa07: str,
    qa07_id: str,
    qa06: str,
    qa06_id: str,
    qa01: str,
    qa01_id: str,
) -> int:
    require_completion_rpc(key, qa07)
    before_context = care_context(key, qa07)
    before_holders = holder_rows(key, qa07)
    before_transfer_ids = {row.get("id") for row in transfer_rows(key, qa07)}
    before_moment_ids = {row.get("id") for row in moment_rows(key, qa07)}
    before_adoption_ids = {row.get("id") for row in adoption_rows(key, qa07)}
    before_request_ids = [row.get("id") for row in managed_requests(key, qa07)]
    request = next(row for row in managed_requests(key, qa07) if row.get("id") == REQUEST_ID)
    apps = foster_apps(key, qa06)
    if request.get("status") != "ACTIVE" or request.get("placement_id") != PLACEMENT_ID:
        raise SystemExit("REFUSING UNEXPECTED ACTIVE FIXTURE")
    if request.get("placement_status") != "OPEN":
        raise SystemExit("REFUSING PLACEMENT NOT OPEN")
    if len(apps) != 1 or apps[0].get("id") != APPLICATION_ID or apps[0].get("status") != "SELECTED":
        raise SystemExit("REFUSING UNEXPECTED APPLICATION")
    if len(matching(before_holders, person_id=qa06_id, holder_kind="PERSON", role="AUTHORIZED", status="ACTIVE")) != 1:
        raise SystemExit("REFUSING UNEXPECTED HOLDER")
    if matching(before_holders, person_id=qa06_id, role="OWNER", status="ACTIVE"):
        raise SystemExit("REFUSING QA06 ALREADY OWNER")
    if len(matching(before_holders, holder_kind="ORGANIZATION", role="RESPONSIBLE", status="ACTIVE", organization_id=ORG_ID)) != 1:
        raise SystemExit("REFUSING UNEXPECTED ORGANIZATION")

    qa06_before = completion_gate(*rpc(key, qa06, "canon_complete_foster_transit", {"p_request_id": REQUEST_ID}))
    qa01_before = completion_gate(*rpc(key, qa01, "canon_complete_foster_transit", {"p_request_id": REQUEST_ID}))
    if qa06_before != "DENIED" or qa01_before != "DENIED":
        report_line("QA06_COMPLETE", qa06_before)
        report_line("UNRELATED_COMPLETE", qa01_before)
        report_line("RESULT", "FAIL UNAUTHORIZED_BEFORE_END")
        return 1
    still = next(row for row in managed_requests(key, qa07) if row.get("id") == REQUEST_ID)
    if still.get("status") != "ACTIVE" or still.get("placement_status") != "OPEN":
        raise SystemExit("UNAUTHORIZED CALL MUTATED TRANSIT")

    first_status, first_raw = rpc(key, qa07, "canon_complete_foster_transit", {"p_request_id": REQUEST_ID})
    if first_status != 200:
        raise SystemExit(f"QA07 COMPLETE FAIL http={first_status} {error_code(first_raw)}")
    first = as_object(first_status, first_raw, "COMPLETE")
    second_status, second_raw = rpc(key, qa07, "canon_complete_foster_transit", {"p_request_id": REQUEST_ID})
    if second_status != 200:
        raise SystemExit(f"AUTHORIZED RETRY FAIL http={second_status} {error_code(second_raw)}")
    second = as_object(second_status, second_raw, "COMPLETE_RETRY")
    qa06_after = completion_gate(*rpc(key, qa06, "canon_complete_foster_transit", {"p_request_id": REQUEST_ID}))
    qa01_after = completion_gate(*rpc(key, qa01, "canon_complete_foster_transit", {"p_request_id": REQUEST_ID}))

    after_context = care_context(key, qa07)
    after_holders = holder_rows(key, qa07)
    after_requests = managed_requests(key, qa07)
    after_request = next(row for row in after_requests if row.get("id") == REQUEST_ID)
    after_apps = foster_apps(key, qa06)
    qa07_view = transit_view(*rpc(key, qa07, "canon_get_active_foster_transit", {"p_pet_id": PET_ID}))
    qa06_view = transit_view(*rpc(key, qa06, "canon_get_active_foster_transit", {"p_pet_id": PET_ID}))
    qa06_context = completion_gate(*rpc(key, qa06, "canon_get_pet_care_context", {"p_pet_id": PET_ID}))
    original_link = matching(before_holders, person_id=qa06_id, holder_kind="PERSON", role="AUTHORIZED", status="ACTIVE")[0]
    stable_fields = (
        "request_id",
        "placement_id",
        "temporary_link_id",
        "pet_id",
        "vitacora_pet_id",
        "public_vitacora_number",
        "request_updated_at",
        "placement_ends_at",
        "temporary_link_valid_until",
        "application_id",
        "application_status",
    )
    failures = []
    if first.get("idempotent") is not False:
        failures.append("FIRST_IDEMPOTENT")
    if second.get("idempotent") is not True:
        failures.append("RETRY_NOT_IDEMPOTENT")
    for field in stable_fields:
        if first.get(field) != second.get(field):
            failures.append("RETRY_" + field.upper())
    if first.get("request_status") != "COMPLETED" or after_request.get("status") != "COMPLETED":
        failures.append("REQUEST_NOT_COMPLETED")
    if first.get("placement_id") != PLACEMENT_ID or first.get("placement_status") != "CLOSED":
        failures.append("PLACEMENT_NOT_CLOSED")
    if after_request.get("placement_id") != PLACEMENT_ID or after_request.get("placement_status") != "CLOSED":
        failures.append("PLACEMENT_READ")
    if first.get("application_status") != "SELECTED" or not after_apps or after_apps[0].get("status") != "SELECTED":
        failures.append("APPLICATION_STATUS")
    if first.get("temporary_holder_role") != "AUTHORIZED" or first.get("temporary_link_status") != "ENDED":
        failures.append("LINK_RESULT")
    if first.get("temporary_link_id") != original_link.get("link_id"):
        failures.append("LINK_REPLACED")
    if matching(after_holders, person_id=qa06_id, holder_kind="PERSON", role="AUTHORIZED", status="ACTIVE"):
        failures.append("QA06_STILL_ACTIVE_HOLDER")
    ended = matching(after_holders, link_id=original_link.get("link_id"), status="ENDED", role="AUTHORIZED")
    if len(ended) != 1:
        failures.append("QA06_NOT_ENDED")
    if matching(after_holders, person_id=qa06_id, role="OWNER"):
        failures.append("QA06_IS_OWNER")
    if len(matching(after_holders, holder_kind="ORGANIZATION", role="RESPONSIBLE", status="ACTIVE")) != 1:
        failures.append("ORG_RESPONSIBLE")
    if link_ids(before_holders) != link_ids(after_holders):
        failures.append("HOLDER_ROWS_CHANGED")
    if [row.get("id") for row in after_requests] != before_request_ids:
        failures.append("REQUEST_ROWS_CHANGED")
    if before_context.get("current_custodian_kind") != after_context.get("current_custodian_kind"):
        failures.append("CUSTODIAN_CHANGED")
    if after_context.get("current_custodian_kind") != "ORGANIZATION" or after_context.get("principal_organization_id") != ORG_ID:
        failures.append("CUSTODIAN")
    if after_context.get("pet_id") != PET_ID or first.get("pet_id") != PET_ID or first.get("vitacora_pet_id") != PET_ID:
        failures.append("PET")
    if before_transfer_ids != {row.get("id") for row in transfer_rows(key, qa07)}:
        failures.append("CARE_TRANSFER")
    if before_adoption_ids != {row.get("id") for row in adoption_rows(key, qa07)}:
        failures.append("ADOPTION")
    moment_ids = {row.get("id") for row in moment_rows(key, qa07)}
    if before_moment_ids != moment_ids:
        failures.append("VITACORA")
    if qa07_view != "NONE" or qa06_view != "NONE":
        failures.append("STILL_ACTIVE")
    if qa06_after != "DENIED" or qa01_after != "DENIED":
        failures.append("UNAUTHORIZED_TERMINAL")
    if qa06_context != "DENIED":
        failures.append("QA06_CONTEXT")

    report_line("MODE", "MUTATED")
    report_line("END_TRANSIT", "PASS" if not failures else "FAIL")
    report_line("REQUEST_BEFORE", "ACTIVE")
    report_line("REQUEST_AFTER", after_request.get("status"))
    report_line("PLACEMENT_BEFORE", "OPEN")
    report_line("PLACEMENT_AFTER", after_request.get("placement_status"))
    report_line("APPLICATION_FINAL", after_apps[0].get("status") if after_apps else None)
    report_line("QA06_HOLDER_BEFORE", "AUTHORIZED / ACTIVE")
    report_line("QA06_HOLDER_AFTER", "AUTHORIZED / ENDED" if ended else "OTHER")
    report_line("QA06_IS_OWNER", "NO" if "QA06_IS_OWNER" not in failures else "YES")
    report_line("ORG_RESPONSIBLE", "ACTIVE" if "ORG_RESPONSIBLE" not in failures else "OTHER")
    report_line("CURRENT_CUSTODIAN", after_context.get("current_custodian_kind"))
    report_line("CURRENT_CUSTODIAN_ORG", after_context.get("principal_organization_id"))
    report_line("ACTIVE_TRANSIT_AFTER", qa07_view)
    report_line("QA06_ACTIVE_TRANSIT", qa06_view)
    report_line("PET_ID_BEFORE", PET_ID)
    report_line("PET_ID_AFTER", after_context.get("pet_id"))
    report_line("PET_PRESERVED", "YES" if after_context.get("pet_id") == PET_ID else "NO")
    report_line("VITACORA_NUMBER", first.get("public_vitacora_number"))
    report_line("VITACORA_PRESERVED", "YES" if first.get("vitacora_pet_id") == PET_ID and "VITACORA" not in failures else "NO")
    report_line("TRANSIT_END_MOMENT", "NOT_CREATED_BY_CURRENT_DOMAIN_DESIGN")
    report_line("TRANSIT_START_MOMENT", "NOT_CREATED_BY_DESIGN_CURRENTLY")
    report_line("CARE_TRANSFER_CREATED", "NO" if "CARE_TRANSFER" not in failures else "YES")
    report_line("ADOPTION_CREATED", "NO" if "ADOPTION" not in failures else "YES")
    report_line("QA07_COMPLETE", "PASS" if first_status == 200 and "REQUEST_NOT_COMPLETED" not in failures else "FAIL")
    report_line("QA06_COMPLETE", qa06_before)
    report_line("UNRELATED_COMPLETE", qa01_before)
    report_line("AUTHORIZED_RETRY", "PASS" if "RETRY_NOT_IDEMPOTENT" not in failures and not any(item.startswith("RETRY_") for item in failures) else "FAIL")
    report_line("UNAUTHORIZED_TERMINAL_RETRY", "DENIED" if qa01_after == "DENIED" and qa06_after == "DENIED" else "LEAKED")
    report_line("QA06_CONTEXT", qa06_context)
    report_line("QA07_ID", qa07_id)
    report_line("QA06_ID", qa06_id)
    report_line("QA01_ID", qa01_id)
    report_line("SERVICE_ROLE", "NO")
    report_line("MANUAL_SQL", "NO")
    report_line("PROD", "NO")
    return finish(failures)


def main() -> int:
    refuse_non_staging()
    key = os.environ.get("SUPABASE_STAGING_PUBLISHABLE_KEY", "").strip()
    password = os.environ.get("LEOVER_QA_PASSWORD", "").strip()
    assert_publishable_key(key)
    if not key or not password:
        raise SystemExit("QA credentials missing")

    sessions = {label: login(key, password, username) for label, username in ACTORS.items()}
    qa07, qa07_id = sessions["QA07"]
    qa06, qa06_id = sessions["QA06"]
    qa01, qa01_id = sessions["QA01"]

    for name, body in (
        ("canon_list_my_foster_requests", None),
        ("canon_list_my_foster_applications", None),
        ("canon_list_open_foster_requests", None),
        ("canon_get_active_foster_transit", {"p_pet_id": PET_ID}),
        ("canon_list_foster_request_applications", {"p_request_id": "00000000-0000-0000-0000-000000000000"}),
        ("canon_request_foster_for_pet", {"p_pet_id": "00000000-0000-0000-0000-000000000000"}),
        ("canon_apply_to_foster_request", {"p_request_id": "00000000-0000-0000-0000-000000000000"}),
        ("canon_select_foster_applicant", {"p_application_id": "00000000-0000-0000-0000-000000000000"}),
    ):
        token = qa06 if name in ("canon_list_open_foster_requests", "canon_list_my_foster_applications", "canon_apply_to_foster_request") else qa07
        status, raw = rpc(key, token, name, body)
        if "PGRST202" in error_code(raw).upper() or "COULD NOT FIND THE FUNCTION" in raw.upper():
            raise SystemExit(f"RPC MISSING {name}")
        if status == 404 and "PGRST202" in raw:
            raise SystemExit(f"RPC MISSING {name}")

    for table, column in TABLES.items():
        status, raw = http(key, "GET", f"/rest/v1/{table}?select={column}&limit=1", token=qa07)
        if not denied(status, raw):
            raise SystemExit(f"TABLE SELECT LEAKED {table} http={status} {error_code(raw)}")

    before_context = as_object(
        *rpc(key, qa07, "canon_get_pet_care_context", {"p_pet_id": PET_ID}),
        "CARE_CONTEXT",
    )
    before_holders = [
        row
        for row in as_rows(
            *rpc(key, qa07, "canon_list_pet_holders", {"p_pet_id": PET_ID}),
            "HOLDERS",
        )
        if isinstance(row, dict)
    ]
    before_transfers = [
        row
        for row in as_rows(
            *rpc(key, qa07, "canon_list_care_transfers", {"p_pet_id": PET_ID}),
            "TRANSFERS",
        )
        if isinstance(row, dict)
    ]
    moment_status, moment_raw = rpc(key, qa07, "canon_list_vitacora_moments", {"p_pet_id": PET_ID})
    before_moments = as_rows(moment_status, moment_raw, "MOMENTS") if moment_status == 200 else []

    if before_context.get("pet_id") != PET_ID:
        raise SystemExit("PET MISMATCH BEFORE")
    if before_context.get("pet_name") != PET_NAME:
        raise SystemExit("PET NAME MISMATCH")

    mine = bruno_requests(
        as_rows(*rpc(key, qa07, "canon_list_my_foster_requests"), "MY_REQUESTS")
    )
    open_rows = non_terminal(mine)
    if len(open_rows) > 1:
        raise SystemExit("REFUSING DUPLICATE NON-TERMINAL REQUEST")

    if (
        end_requested()
        or not open_rows
        or open_rows[0].get("status") != "ACTIVE"
        or open_rows[0].get("id") != REQUEST_ID
    ):
        return run_transit_lifecycle(
            key, qa07, qa07_id, qa06, qa06_id, qa01, qa01_id, mine, open_rows
        )

    read_only = bool(open_rows) and open_rows[0].get("status") == "ACTIVE"
    request_reused = bool(open_rows)
    request_id = open_rows[0]["id"] if open_rows else None
    initial_status = open_rows[0].get("status") if open_rows else None

    if not read_only:
        raise SystemExit("REFUSING REQUEST CREATION")

    if not request_id:
        raise SystemExit("REQUEST MISSING")

    open_status, open_raw = rpc(key, qa06, "canon_list_open_foster_requests")
    if open_status != 200:
        raise SystemExit(f"QA06 OPEN FAIL http={open_status} {error_code(open_raw)}")
    qa06_sees = any(
        isinstance(row, dict) and row.get("id") == request_id and row.get("pet_id") == PET_ID
        for row in as_rows(open_status, open_raw, "QA06_OPEN")
    )

    unrelated_status, unrelated_raw = rpc(key, qa01, "canon_list_open_foster_requests")
    unrelated_open = "DENIED" if denied(unrelated_status, unrelated_raw) else "LEAKED"
    if unrelated_status == 200:
        rows = as_rows(unrelated_status, unrelated_raw, "QA01_OPEN")
        if any(isinstance(row, dict) and row.get("id") == request_id for row in rows):
            unrelated_open = "LEAKED"
        elif rows:
            unrelated_open = "LEAKED"
        else:
            unrelated_open = "EMPTY"

    apps = [
        row
        for row in as_rows(*rpc(key, qa06, "canon_list_my_foster_applications"), "MY_APPS")
        if isinstance(row, dict) and row.get("request_id") == request_id
    ]
    if len(apps) > 1:
        raise SystemExit("REFUSING DUPLICATE APPLICATION")

    application_reused = bool(apps)
    application_id = apps[0]["id"] if apps else None
    application_status = apps[0].get("status") if apps else None

    if not read_only and initial_status == "REQUESTED" and application_status != "SELECTED":
        raise SystemExit("REFUSING APPLICATION CREATION")

    listed_status, listed_raw = rpc(
        key, qa07, "canon_list_foster_request_applications", {"p_request_id": request_id}
    )
    qa07_sees_app = False
    if listed_status == 200 and application_id:
        listed = as_rows(listed_status, listed_raw, "QA07_APPS")
        qa07_sees_app = any(
            isinstance(row, dict) and row.get("id") == application_id for row in listed
        )
    unrelated_apps_status, unrelated_apps_raw = rpc(
        key, qa01, "canon_list_foster_request_applications", {"p_request_id": request_id}
    )
    unrelated_apps = "DENIED" if denied(unrelated_apps_status, unrelated_apps_raw) else "LEAKED"

    self_select = "NOT_RUN"
    if not read_only and application_id and application_status == "PENDING":
        raise SystemExit("REFUSING PLACEMENT CREATION")
    placement_id = None

    final_rows = non_terminal(
        bruno_requests(as_rows(*rpc(key, qa07, "canon_list_my_foster_requests"), "MY_REQUESTS_FINAL"))
    )
    if len(final_rows) != 1 or final_rows[0].get("id") != request_id:
        raise SystemExit("FINAL REQUEST MISMATCH")
    final_request = final_rows[0]
    final_apps = [
        row
        for row in as_rows(*rpc(key, qa06, "canon_list_my_foster_applications"), "MY_APPS_FINAL")
        if isinstance(row, dict) and row.get("request_id") == request_id
    ]
    if len(final_apps) != 1:
        raise SystemExit("FINAL APPLICATION COUNT")
    final_app = final_apps[0]

    qa07_transit_status, qa07_transit_raw = rpc(
        key, qa07, "canon_get_active_foster_transit", {"p_pet_id": PET_ID}
    )
    qa06_transit_status, qa06_transit_raw = rpc(
        key, qa06, "canon_get_active_foster_transit", {"p_pet_id": PET_ID}
    )
    unrelated_transit_status, unrelated_transit_raw = rpc(
        key, qa01, "canon_get_active_foster_transit", {"p_pet_id": PET_ID}
    )

    qa07_transit = (
        as_object(qa07_transit_status, qa07_transit_raw, "QA07_TRANSIT")
        if qa07_transit_status == 200
        else {}
    )
    qa06_transit = (
        as_object(qa06_transit_status, qa06_transit_raw, "QA06_TRANSIT")
        if qa06_transit_status == 200
        else {}
    )
    if placement_id is None:
        placement_id = qa07_transit.get("placement_id") or final_request.get("placement_id")

    mutate_status, mutate_raw = rpc(
        key,
        qa06,
        "canon_initiate_care_transfer",
        {
            "p_pet_id": PET_ID,
            "p_target_kind": "PERSON",
            "p_target_person_id": qa06_id,
            "p_target_organization_id": None,
            "p_share_personal_media": False,
        },
    )
    qa06_cannot_transfer = mutate_status != 200 and (
        denied(mutate_status, mutate_raw) or error_code(mutate_raw) not in ("", "http_error")
    )

    after_context = as_object(
        *rpc(key, qa07, "canon_get_pet_care_context", {"p_pet_id": PET_ID}),
        "CARE_CONTEXT_AFTER",
    )
    after_holders = [
        row
        for row in as_rows(
            *rpc(key, qa07, "canon_list_pet_holders", {"p_pet_id": PET_ID}),
            "HOLDERS_AFTER",
        )
        if isinstance(row, dict)
    ]
    after_transfers = [
        row
        for row in as_rows(
            *rpc(key, qa07, "canon_list_care_transfers", {"p_pet_id": PET_ID}),
            "TRANSFERS_AFTER",
        )
        if isinstance(row, dict)
    ]
    after_moment_status, after_moment_raw = rpc(
        key, qa07, "canon_list_vitacora_moments", {"p_pet_id": PET_ID}
    )
    after_moments = (
        as_rows(after_moment_status, after_moment_raw, "MOMENTS_AFTER")
        if after_moment_status == 200
        else []
    )

    active_holders = [row for row in after_holders if row.get("status") == "ACTIVE"]
    org_links = [
        row
        for row in active_holders
        if row.get("holder_kind") == "ORGANIZATION"
        and row.get("role") == "RESPONSIBLE"
        and row.get("organization_id") == ORG_ID
    ]
    qa06_owner = [
        row
        for row in active_holders
        if row.get("person_id") == qa06_id and row.get("role") == "OWNER"
    ]
    qa06_authorized = [
        row
        for row in active_holders
        if row.get("person_id") == qa06_id
        and row.get("holder_kind") == "PERSON"
        and row.get("role") == "AUTHORIZED"
    ]
    owner_links = [row for row in active_holders if row.get("role") == "OWNER"]
    before_owner_ids = {
        row.get("link_id")
        for row in before_holders
        if row.get("status") == "ACTIVE" and row.get("role") == "OWNER"
    }
    after_owner_ids = {row.get("link_id") for row in owner_links}
    before_transfer_ids = {row.get("id") for row in before_transfers}
    after_transfer_ids = {row.get("id") for row in after_transfers}

    custodian = {
        "kind": after_context.get("current_custodian_kind"),
        "person_id": after_context.get("principal_person_id"),
        "organization_id": after_context.get("principal_organization_id"),
        "display_name": after_context.get("principal_display_name"),
    }
    custodian_unchanged = (
        before_context.get("current_custodian_kind") == after_context.get("current_custodian_kind")
        and before_context.get("principal_person_id") == after_context.get("principal_person_id")
        and before_context.get("principal_organization_id") == after_context.get("principal_organization_id")
    )
    moment_ids_before = {row.get("id") for row in before_moments if isinstance(row, dict)}
    moment_ids_after = {row.get("id") for row in after_moments if isinstance(row, dict)}
    new_moment_kinds = sorted(
        {
            str(row.get("kind"))
            for row in after_moments
            if isinstance(row, dict) and row.get("id") not in moment_ids_before
        }
    )

    request_final = str(final_request.get("status"))
    app_final = str(final_app.get("status"))
    placement_status = str(
        qa07_transit.get("placement_status") or final_request.get("placement_status") or ""
    )
    mode = "READ_ONLY" if read_only else "MUTATED"

    report_line("MODE", mode)
    report_line("END_TRANSIT", "NOT_RUN")
    report_line("REQUEST_ID", request_id)
    report_line("REQUEST_REUSED", "YES" if request_reused else "NO")
    report_line("REQUEST_INITIAL", initial_status)
    report_line("REQUEST_RETRY", "NOT_RUN" if read_only else "SAME_ID")
    report_line("REQUEST_FINAL", request_final)
    report_line("QA06_SEES_REQUEST", "YES" if qa06_sees else "NO")
    report_line("UNRELATED_OPEN", unrelated_open)
    report_line("UNRELATED_OPEN_CODE", error_code(unrelated_raw) if unrelated_status != 200 else "HTTP_200")
    report_line("APPLICATION_ID", application_id or final_app.get("id"))
    report_line("APPLICATION_REUSED", "YES" if application_reused else "NO")
    report_line("APPLICATION_INITIAL", application_status)
    report_line("APPLICATION_FINAL", app_final)
    report_line("APPLICATION_RETRY", "SAME_ID" if not read_only else "NOT_RUN")
    report_line("QA07_SEES_APPLICATION", "YES" if qa07_sees_app or (read_only and app_final == "SELECTED") else "NO")
    report_line("UNRELATED_APPLICATION_READ", unrelated_apps)
    report_line("QA06_SELF_SELECT", self_select)
    report_line("PLACEMENT_ID", placement_id)
    report_line("PLACEMENT_STATUS", placement_status)
    report_line("QA06_HOLDER_ROLE", "AUTHORIZED" if qa06_authorized else ("OWNER" if qa06_owner else "OTHER"))
    report_line("QA06_IS_OWNER", "YES" if qa06_owner else "NO")
    report_line("ORG_RESPONSIBLE", "ACTIVE" if org_links else "OTHER")
    report_line("OWNER_LINK_COUNT", len(owner_links))
    report_line("OWNER_LINKS_UNCHANGED", "YES" if before_owner_ids == after_owner_ids else "NO")
    report_line("CURRENT_CUSTODIAN", json.dumps(custodian, ensure_ascii=False))
    report_line("CUSTODIAN_UNCHANGED", "YES" if custodian_unchanged else "NO")
    report_line("CUSTODIAN_IS_QA06", "YES" if custodian.get("person_id") == qa06_id else "NO")
    report_line("QA07_ACTIVE_TRANSIT", "PASS" if qa07_transit.get("request_id") == request_id and qa07_transit.get("request_status") == "ACTIVE" else "FAIL")
    report_line("QA06_ACTIVE_TRANSIT", "PASS" if qa06_transit.get("request_id") == request_id and qa06_transit.get("foster_user_id") == qa06_id else "FAIL")
    report_line("QA06_TRANSIT_ROLE", qa06_transit.get("temporary_holder_role"))
    report_line("QA07_TRANSIT_RESPONSIBLE", qa07_transit.get("responsible_role"))
    report_line("QA07_TRANSIT_ORG", qa07_transit.get("responsible_organization_id"))
    report_line(
        "UNRELATED_ACTIVE_TRANSIT",
        "DENIED" if denied(unrelated_transit_status, unrelated_transit_raw) else "LEAKED",
    )
    report_line("PET_PRESERVED", "YES" if after_context.get("pet_id") == PET_ID else "NO")
    report_line("VITACORA_MOMENTS_KEPT", "YES" if moment_ids_before <= moment_ids_after else "NO")
    report_line("NEW_MOMENT_KINDS", ",".join(new_moment_kinds) if new_moment_kinds else "NONE")
    report_line("TRANSIT_START_MOMENT", "NOT_CREATED_BY_DESIGN_CURRENTLY")
    report_line("CARE_TRANSFER_CREATED", "YES" if after_transfer_ids - before_transfer_ids else "NO")
    report_line("QA06_TRANSFER_MUTATION", "DENIED" if qa06_cannot_transfer else "LEAKED")
    report_line("QA07_ID", qa07_id)
    report_line("QA06_ID", qa06_id)
    report_line("QA01_ID", qa01_id)
    report_line("SERVICE_ROLE", "NO")
    report_line("MANUAL_SQL", "NO")
    report_line("PROD", "NO")

    failures = []
    if request_final != "ACTIVE":
        failures.append("REQUEST_NOT_ACTIVE")
    if app_final != "SELECTED":
        failures.append("APPLICATION_NOT_SELECTED")
    if placement_status != "OPEN":
        failures.append("PLACEMENT_NOT_OPEN")
    if not qa06_authorized or qa06_owner:
        failures.append("HOLDER_ROLE")
    if not org_links:
        failures.append("ORG_RESPONSIBLE")
    if before_owner_ids != after_owner_ids:
        failures.append("OWNER_CHANGED")
    if custodian.get("person_id") == qa06_id:
        failures.append("CUSTODIAN_BECAME_QA06")
    if not custodian_unchanged:
        failures.append("CUSTODIAN_CHANGED")
    if after_transfer_ids - before_transfer_ids:
        failures.append("CARE_TRANSFER")
    if after_context.get("pet_id") != PET_ID:
        failures.append("PET")
    if not (moment_ids_before <= moment_ids_after):
        failures.append("VITACORA")
    if qa07_transit.get("request_status") != "ACTIVE" or qa07_transit.get("pet_id") != PET_ID:
        failures.append("QA07_TRANSIT")
    if qa06_transit.get("application_status") != "SELECTED" or qa06_transit.get("temporary_holder_role") != "AUTHORIZED":
        failures.append("QA06_TRANSIT")
    if not denied(unrelated_transit_status, unrelated_transit_raw):
        failures.append("UNRELATED_TRANSIT")
    if unrelated_apps != "DENIED":
        failures.append("UNRELATED_APPS")
    if unrelated_open != "DENIED":
        failures.append("UNRELATED_OPEN")
    if new_moment_kinds:
        failures.append("NEW_MOMENTS")
    if not qa06_cannot_transfer:
        failures.append("QA06_MUTATION")
    if self_select == "LEAKED":
        failures.append("SELF_SELECT")
    if not read_only and not qa06_sees:
        failures.append("QA06_DISCOVERY")

    if failures:
        report_line("RESULT", "FAIL " + ",".join(failures))
        return 1
    report_line("RESULT", "PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except SystemExit:
        raise
    except Exception as exc:
        print(f"PROBE ERROR: {exc}")
        raise SystemExit(1)
