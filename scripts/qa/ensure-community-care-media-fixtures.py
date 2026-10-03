#!/usr/bin/env python3
"""Ensure QA01 and QA02 each own one real STAGING image media asset.

Canonical Android path, user sessions only:
  canon_begin_username_login
  → password session
  → canon_register_media (row is created before bytes, lifecycle READY)
  → POST /storage/v1/object/{bucket}/{path} with that user's JWT

Bucket is public-media, the physical bucket for LOST_FOUND_MEDIA.
There is no separate finalize RPC. Signed URLs are a private read path,
not the upload path.

Staging project only: tobqbddfcyitwgbkthhy.
Refuses the legacy/prod ref. Does not use service_role, a database
password, or SQL inserts into media_assets. Does not create lost/found.
"""

from __future__ import annotations

import base64
import hashlib
import json
import os
import struct
import sys
import urllib.error
import urllib.parse
import urllib.request
import zlib

STAGING_REF = "tobqbddfcyitwgbkthhy"
LEGACY_PROD_REF = "wystsapjfpdtoprlmizz"
BASE = f"https://{STAGING_REF}.supabase.co"
BUCKET = "public-media"
MIME = "image/png"
MIN_DIMENSION = 256
MIN_REUSE_BYTES = 1024
ALLOWED_IMAGE_MIME = {
    "image/jpeg",
    "image/png",
    "image/webp",
    "image/heic",
    "image/heif",
    "image/gif",
}
ACTORS = ("qa01owner", "qa02finder")


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


def http(
    key: str,
    method: str,
    path: str,
    body=None,
    token: str | None = None,
    raw: bytes | None = None,
    content_type: str | None = "application/json",
    extra: dict | None = None,
    timeout: int = 60,
):
    headers = {"apikey": key, "Accept": "application/json"}
    if content_type:
        headers["Content-Type"] = content_type
    if token:
        headers["Authorization"] = "Bearer " + token
    if extra:
        headers.update(extra)
    data = raw if raw is not None else (None if body is None else json.dumps(body).encode())
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read(), dict(resp.headers)
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read(), dict(exc.headers)


def safe_error(raw: bytes) -> str:
    text = raw.decode(errors="replace")
    try:
        body = json.loads(text)
    except json.JSONDecodeError:
        return "http_error"
    if isinstance(body, dict):
        code = body.get("code") or body.get("error") or body.get("error_code") or ""
        message = str(body.get("message") or body.get("msg") or "")
        upper = message.upper()
        for marker in (
            "NOT_AUTHENTICATED",
            "FORBIDDEN",
            "VALIDATION",
            "RATE_LIMITED",
            "QUOTA_EXCEEDED",
            "FILE_TOO_LARGE",
            "NOT_FOUND",
        ):
            if marker in upper:
                return marker
        if code:
            return str(code)[:80]
    return "http_error"


def login(key: str, password: str, username: str) -> tuple[str, str]:
    status, raw, _ = http(
        key,
        "POST",
        "/rest/v1/rpc/canon_begin_username_login",
        {"p_username": username, "p_password": password},
    )
    if status != 200:
        raise SystemExit(f"{username} AUTH FAIL begin http={status} {safe_error(raw)}")
    body = json.loads(raw.decode())
    if isinstance(body, str):
        body = json.loads(body)
    email = body.get("email") if isinstance(body, dict) else None
    if not email:
        raise SystemExit(f"{username} AUTH FAIL missing session email")
    status, raw, _ = http(
        key,
        "POST",
        "/auth/v1/token?grant_type=password",
        {"email": email, "password": password},
    )
    if status != 200:
        raise SystemExit(f"{username} AUTH FAIL session http={status}")
    token = json.loads(raw.decode()).get("access_token")
    if not token:
        raise SystemExit(f"{username} AUTH FAIL missing session")
    status, raw, _ = http(key, "GET", "/auth/v1/user", token=token)
    if status != 200:
        raise SystemExit(f"{username} AUTH FAIL user http={status}")
    user_id = json.loads(raw.decode()).get("id")
    if not user_id:
        raise SystemExit(f"{username} AUTH FAIL missing user id")
    return token, user_id


def list_assets(key: str, token: str) -> list[dict]:
    status, raw, _ = http(
        key,
        "GET",
        "/rest/v1/media_assets"
        "?select=id,bucket,object_path,mime_type,byte_size,owner_kind,"
        "owner_person_id,created_by,lifecycle_status,visibility"
        "&order=created_at.desc&limit=100",
        token=token,
    )
    if status != 200:
        raise SystemExit(f"LIST FAIL http={status} {safe_error(raw)}")
    rows = json.loads(raw.decode())
    if not isinstance(rows, list):
        raise SystemExit("LIST FAIL unexpected payload")
    return rows


def encode_path(path: str) -> str:
    return "/".join(urllib.parse.quote(part, safe="") for part in path.split("/"))


def download_object(key: str, token: str, bucket: str, path: str) -> bytes | None:
    encoded = encode_path(path)
    for route in (
        f"/storage/v1/object/public/{bucket}/{encoded}",
        f"/storage/v1/object/authenticated/{bucket}/{encoded}",
    ):
        status, raw, _ = http(
            key,
            "GET",
            route,
            token=token,
            content_type=None,
        )
        if status == 200 and raw:
            return raw
    return None


def image_dimensions(data: bytes, mime: str) -> tuple[int, int] | None:
    if mime == "image/png" or data.startswith(b"\x89PNG\r\n\x1a\n"):
        if len(data) < 24 or data[12:16] != b"IHDR":
            return None
        width, height = struct.unpack(">II", data[16:24])
        return width, height
    if mime == "image/jpeg" or data.startswith(b"\xff\xd8"):
        return jpeg_dimensions(data)
    return None


def jpeg_dimensions(data: bytes) -> tuple[int, int] | None:
    if len(data) < 4 or data[:2] != b"\xff\xd8":
        return None
    index = 2
    while index + 9 < len(data):
        if data[index] != 0xFF:
            index += 1
            continue
        marker = data[index + 1]
        if marker in (0xC0, 0xC1, 0xC2):
            height, width = struct.unpack(">HH", data[index + 5 : index + 9])
            return width, height
        if marker in (0xD8, 0xD9):
            index += 2
            continue
        if index + 4 > len(data):
            return None
        segment = struct.unpack(">H", data[index + 2 : index + 4])[0]
        if segment < 2:
            return None
        index += 2 + segment
    return None


def asset_is_usable(key: str, token: str, user_id: str, row: dict) -> bool:
    if row.get("owner_person_id") != user_id:
        return False
    if row.get("created_by") != user_id:
        return False
    if row.get("owner_kind") != "PERSON":
        return False
    if row.get("lifecycle_status") != "READY":
        return False
    mime = str(row.get("mime_type") or "").lower()
    if mime not in ALLOWED_IMAGE_MIME:
        return False
    try:
        declared = int(row.get("byte_size") or 0)
    except (TypeError, ValueError):
        return False
    if declared < MIN_REUSE_BYTES:
        return False
    blob = download_object(key, token, str(row.get("bucket") or ""), str(row.get("object_path") or ""))
    if not blob or len(blob) < MIN_REUSE_BYTES:
        return False
    dims = image_dimensions(blob, mime)
    if dims is None:
        return False
    return dims[0] >= MIN_DIMENSION and dims[1] >= MIN_DIMENSION


def synthetic_png(width: int = 256, height: int = 256) -> bytes:
    """Deterministic test card. No personal data and no external asset."""
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            red = (x * 13 + y * 7) & 255
            green = (x * 3 + 40) & 255
            blue = (y * 11 + 90) & 255
            if (x // 16 + y // 16) % 2 == 0:
                red = 255 - red
            if x < 10 or y < 10 or x >= width - 10 or y >= height - 10:
                red, green, blue = 18, 92, 168
            raw.extend((red, green, blue))
    def chunk(tag: bytes, payload: bytes) -> bytes:
        return (
            struct.pack(">I", len(payload))
            + tag
            + payload
            + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF)
        )

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")


def register_media(key: str, token: str, path: str, size: int) -> str:
    status, raw, _ = http(
        key,
        "POST",
        "/rest/v1/rpc/canon_register_media",
        {
            "p_bucket": BUCKET,
            "p_path": path,
            "p_mime": MIME,
            "p_size": size,
        },
        token=token,
    )
    if status != 200:
        raise SystemExit(f"REGISTER FAIL http={status} {safe_error(raw)}")
    body = json.loads(raw.decode())
    if isinstance(body, str) and body:
        return body
    raise SystemExit("REGISTER FAIL unexpected id")


def upload_bytes(key: str, token: str, path: str, blob: bytes) -> None:
    status, raw, _ = http(
        key,
        "POST",
        f"/storage/v1/object/{BUCKET}/{encode_path(path)}",
        token=token,
        raw=blob,
        content_type=MIME,
        extra={"x-upsert": "false", "cache-control": "3600"},
    )
    if status not in (200, 201):
        raise SystemExit(f"UPLOAD FAIL http={status} {safe_error(raw)}")


def read_asset(key: str, token: str, asset_id: str) -> dict | None:
    status, raw, _ = http(
        key,
        "GET",
        "/rest/v1/media_assets"
        "?select=id,bucket,object_path,mime_type,byte_size,owner_kind,"
        "owner_person_id,created_by,lifecycle_status,visibility"
        f"&id=eq.{urllib.parse.quote(asset_id)}",
        token=token,
    )
    if status != 200:
        return None
    rows = json.loads(raw.decode())
    if isinstance(rows, list) and rows:
        return rows[0]
    return None


def fixture_path(user_id: str) -> str:
    # LOST_FOUND_MEDIA shape: lost_found/{caseId}/{assetId}/{file}
    # The case segment is a fixture label, not a lost_found_alerts row.
    return f"lost_found/qa-community-care/{user_id}/qa-fixture.png"


def ensure_actor(key: str, password: str, username: str) -> dict:
    token, user_id = login(key, password, username)
    print(f"{username} AUTH PASS")
    rows = list_assets(key, token)
    for row in rows:
        if asset_is_usable(key, token, user_id, row):
            print(f"{username} EXISTING ASSET REUSED: YES")
            return {
                "username": username,
                "token": token,
                "user_id": user_id,
                "reused": True,
                "asset": row,
            }

    blob = synthetic_png()
    dims = image_dimensions(blob, MIME)
    if dims is None or dims[0] < MIN_DIMENSION or dims[1] < MIN_DIMENSION or len(blob) < MIN_REUSE_BYTES:
        raise SystemExit(f"{username} IMAGE FAIL synthetic card is not a usable image")
    path = fixture_path(user_id)
    pending = next(
        (
            row
            for row in rows
            if row.get("owner_person_id") == user_id
            and row.get("bucket") == BUCKET
            and row.get("object_path") == path
        ),
        None,
    )
    if pending is None:
        asset_id = register_media(key, token, path, len(blob))
    else:
        asset_id = str(pending["id"])
        upload_bytes(key, token, path, blob)
        row = read_asset(key, token, asset_id)
        if row is None or not asset_is_usable(key, token, user_id, row):
            raise SystemExit(f"{username} VERIFY FAIL asset not readable after upload")
        downloaded = download_object(key, token, BUCKET, path)
        if downloaded is None or hashlib.sha256(downloaded).digest() != hashlib.sha256(blob).digest():
            raise SystemExit(f"{username} VERIFY FAIL storage bytes mismatch")
        print(f"{username} EXISTING ASSET REUSED: NO")
        return {
            "username": username,
            "token": token,
            "user_id": user_id,
            "reused": False,
            "asset": row,
        }

    upload_bytes(key, token, path, blob)
    row = read_asset(key, token, asset_id)
    if row is None or not asset_is_usable(key, token, user_id, row):
        raise SystemExit(f"{username} VERIFY FAIL asset not readable after upload")
    downloaded = download_object(key, token, BUCKET, path)
    if downloaded is None or hashlib.sha256(downloaded).digest() != hashlib.sha256(blob).digest():
        raise SystemExit(f"{username} VERIFY FAIL storage bytes mismatch")
    print(f"{username} EXISTING ASSET REUSED: NO")
    return {
        "username": username,
        "token": token,
        "user_id": user_id,
        "reused": False,
        "asset": row,
    }


def mutation_denied(key: str, actor: dict, other: dict) -> bool:
    asset_id = other["asset"]["id"]
    path = other["asset"]["object_path"]
    status, raw, _ = http(
        key,
        "PATCH",
        f"/rest/v1/media_assets?id=eq.{urllib.parse.quote(asset_id)}",
        {"mime_type": other["asset"].get("mime_type") or MIME},
        token=actor["token"],
        extra={"Prefer": "return=representation"},
    )
    body = b""
    try:
        parsed = json.loads(raw.decode() or "null")
    except json.JSONDecodeError:
        parsed = None
    patch_ok = status in (200, 204) and isinstance(parsed, list) and len(parsed) > 0
    if status in (200, 204) and isinstance(parsed, dict) and parsed.get("id"):
        patch_ok = True
    storage_status, storage_raw, _ = http(
        key,
        "POST",
        f"/storage/v1/object/{other['asset']['bucket']}/{encode_path(path)}",
        token=actor["token"],
        raw=b"qa-cross-user",
        content_type="image/png",
        extra={"x-upsert": "false"},
    )
    storage_ok = storage_status in (200, 201)
    visible = read_asset(key, actor["token"], asset_id)
    # A private asset must stay invisible. A returned row would be a read,
    # recorded separately from mutation.
    leaked = patch_ok or storage_ok
    print(
        f"CROSS {actor['username']}->{other['username']} "
        f"patch={status} storage={storage_status} "
        f"visible={'YES' if visible else 'NO'} "
        f"mutation={'LEAK' if leaked else 'DENIED'}"
    )
    if leaked:
        _ = body
        _ = storage_raw
    return not leaked


def print_asset(username: str, row: dict) -> None:
    print(f"{username} MEDIA ASSET")
    print(f"id: {row.get('id')}")
    print(f"mime: {row.get('mime_type')}")
    print(f"status: {row.get('lifecycle_status')}")
    print(f"bucket: {row.get('bucket')}")
    print(f"path: {row.get('object_path')}")
    print(f"owner_kind: {row.get('owner_kind')}")
    print(f"owner_person_id: {row.get('owner_person_id')}")
    print(f"created_by: {row.get('created_by')}")
    print(f"visibility: {row.get('visibility')}")
    print(f"byte_size: {row.get('byte_size')}")


def main() -> int:
    refuse_non_staging()
    key = os.environ.get("SUPABASE_STAGING_PUBLISHABLE_KEY", "").strip()
    password = os.environ.get("LEOVER_QA_PASSWORD", "").strip()
    if not key or not password:
        print("MISSING_SECRET SUPABASE_STAGING_PUBLISHABLE_KEY or LEOVER_QA_PASSWORD")
        return 2
    assert_publishable_key(key)
    print(f"STAGING: {STAGING_REF}")
    print("SERVICE ROLE: NO")
    print("MANUAL MEDIA_ASSETS INSERT: NO")
    print("PROD: NO")

    results = [ensure_actor(key, password, username) for username in ACTORS]
    for result in results:
        print_asset(result["username"], result["asset"])
        if result["asset"].get("owner_person_id") != result["user_id"]:
            print(f"{result['username']} OWNER MISMATCH")
            return 1
        if result["asset"].get("owner_person_id") == results[1 - results.index(result)]["user_id"]:
            print(f"{result['username']} CROSS OWNER")
            return 1

    denied = mutation_denied(key, results[0], results[1]) and mutation_denied(key, results[1], results[0])
    # Confirm the same-mime probe did not change the owner's row.
    for result in results:
        fresh = read_asset(key, result["token"], result["asset"]["id"])
        if fresh is None or fresh.get("mime_type") != result["asset"].get("mime_type"):
            print("CROSS USER MUTATION LEAK: YES")
            return 1
        if fresh.get("owner_person_id") != result["user_id"]:
            print("CROSS USER MUTATION LEAK: YES")
            return 1
    print("CANONICAL UPLOAD PATH: PASS")
    print(f"CROSS USER MUTATION LEAK: {'NO' if denied else 'YES'}")
    if not denied:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
