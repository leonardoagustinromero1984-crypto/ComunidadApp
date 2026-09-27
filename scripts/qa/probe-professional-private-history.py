#!/usr/bin/env python3
"""Live STAGING probe for provider-private clinical history.

Uses QA user sessions only. Requires:
  SUPABASE_STAGING_PUBLISHABLE_KEY
  LEOVER_QA_PASSWORD

Never prints tokens, passwords, or the clinical summary text.
Target project is tobqbddfcyitwgbkthhy. Does not call service_role.
"""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request

BASE = "https://tobqbddfcyitwgbkthhy.supabase.co"
PRIVATE_MARKERS = ("qa privado", "control clínico", "solo historial")
ACTORS = {
    "QA11": "qa11.pro.a@leoverapp.com",
    "QA16": "qa16.vet.norte@leoverapp.com",
    "QA01": "qa01.owner@leoverapp.com",
}


def http(key: str, method: str, path: str, body=None, token: str | None = None):
    headers = {
        "apikey": key,
        "Content-Type": "application/json",
        "Accept": "application/json",
    }
    if token:
        headers["Authorization"] = "Bearer " + token
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=40) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode()


def login(key: str, password: str, email: str) -> str:
    status, raw = http(
        key,
        "POST",
        "/auth/v1/token?grant_type=password",
        {"email": email, "password": password},
    )
    if status != 200:
        raise SystemExit(f"AUTH FAIL http={status}")
    token = json.loads(raw).get("access_token")
    if not token:
        raise SystemExit("AUTH FAIL missing session")
    return token


def error_code(raw: str) -> str:
    try:
        body = json.loads(raw)
    except json.JSONDecodeError:
        return ""
    if isinstance(body, dict):
        return str(body.get("code") or body.get("message") or "")
    return ""


def as_list(raw: str):
    body = json.loads(raw)
    if isinstance(body, str):
        body = json.loads(body)
    return body


def has_private(raw: str) -> bool:
    lowered = raw.lower()
    return any(marker in lowered for marker in PRIVATE_MARKERS)


def main() -> int:
    key = os.environ.get("SUPABASE_STAGING_PUBLISHABLE_KEY", "").strip()
    password = os.environ.get("LEOVER_QA_PASSWORD", "").strip()
    if not key or not password:
        print("MISSING_SECRET SUPABASE_STAGING_PUBLISHABLE_KEY or LEOVER_QA_PASSWORD")
        return 2
    if "service_role" in key:
        print("REFUSING service_role key")
        return 2

    tokens = {name: login(key, password, email) for name, email in ACTORS.items()}
    print("AUTH QA11 PASS")
    print("AUTH QA16 PASS")
    print("AUTH QA01 PASS")

    status, raw = http(
        key,
        "POST",
        "/rest/v1/rpc/canon_search_professional_patients",
        {"p_query": "Mora"},
        tokens["QA11"],
    )
    rows = as_list(raw) if status == 200 else []
    pet_id = rows[0]["id"] if isinstance(rows, list) and rows else None
    search_leak = has_private(raw)
    print(
        f"PATIENT_SEARCH QA11 http={status} found={bool(pet_id)} "
        f"leak={'YES' if search_leak else 'NO'}"
    )

    status16, raw16 = http(
        key,
        "POST",
        "/rest/v1/rpc/canon_search_professional_patients",
        {"p_query": "Mora"},
        tokens["QA16"],
    )
    rows16 = as_list(raw16) if status16 == 200 else None
    print(
        f"PATIENT_SEARCH QA16 http={status16} "
        f"count={len(rows16) if isinstance(rows16, list) else 'ERR'} "
        f"leak={'YES' if has_private(raw16) else 'NO'}"
    )

    status_sel, raw_sel = http(
        key,
        "GET",
        "/rest/v1/veterinary_care_records?select=id&limit=1",
        token=tokens["QA11"],
    )
    print(f"DIRECT_SELECT QA11 http={status_sel} code={error_code(raw_sel)}")

    if not pet_id:
        print("QA11_READ FAIL no patient id")
        return 1

    def history(actor: str):
        return http(
            key,
            "POST",
            "/rest/v1/rpc/canon_list_professional_pet_cares",
            {"p_pet_id": pet_id},
            tokens[actor],
        )

    st11, raw11 = history("QA11")
    qa11_pass = st11 == 200 and has_private(raw11)
    print(
        f"QA11_READ http={st11} code={error_code(raw11)} "
        f"private={'YES' if has_private(raw11) else 'NO'} "
        f"result={'PASS' if qa11_pass else 'FAIL'}"
    )

    st16, raw16h = history("QA16")
    qa16_code = error_code(raw16h)
    qa16_denied = (not has_private(raw16h)) and ("FORBIDDEN" in qa16_code or qa16_code == "P0001")
    if has_private(raw16h):
        qa16_result = "LEAKED"
    elif qa16_denied:
        qa16_result = "DENIED"
    else:
        qa16_result = "NOT_TESTED"
    print(
        f"QA16_READ http={st16} code={qa16_code} "
        f"leak={'YES' if has_private(raw16h) else 'NO'} "
        f"result={qa16_result}"
    )

    st01, raw01 = history("QA01")
    owner_rpc_leak = has_private(raw01)
    print(
        f"OWNER_RPC http={st01} code={error_code(raw01)} "
        f"leak={'YES' if owner_rpc_leak else 'NO'}"
    )

    st_m, raw_m = http(
        key,
        "POST",
        "/rest/v1/rpc/canon_list_vitacora_moments",
        {"p_pet_id": pet_id},
        tokens["QA01"],
    )
    st_p, raw_p = http(
        key,
        "POST",
        "/rest/v1/rpc/canon_list_vitacora_proposals",
        {"p_pet_id": pet_id},
        tokens["QA01"],
    )
    owner_leak = has_private(raw_m) or has_private(raw_p) or owner_rpc_leak
    print(
        f"OWNER_VITACORA moments={st_m} proposals={st_p} "
        f"leak={'YES' if owner_leak else 'NO'}"
    )

    ok = (
        status == 200
        and pet_id
        and not search_leak
        and status16 == 200
        and isinstance(rows16, list)
        and len(rows16) == 0
        and not has_private(raw16)
        and status_sel == 403
        and qa11_pass
        and qa16_denied
        and not owner_leak
    )
    print("PROBE", "PASS" if ok else "FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
