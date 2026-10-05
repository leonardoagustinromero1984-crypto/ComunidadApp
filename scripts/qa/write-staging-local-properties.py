#!/usr/bin/env python3
"""Write gitignored local.properties for the staging physical QA assemble.

Signing material and client keys are read from the environment (GitHub Actions
secrets or the Cursor secret store). This script never prints those values.
"""

from __future__ import annotations

import argparse
import base64
import os
import stat
import sys
from pathlib import Path

STAGING_URL = "https://tobqbddfcyitwgbkthhy.supabase.co"
MAPS_SENTINELS = {
    "",
    "MAPS_API_KEY_MISSING",
    "YOUR_KEY_HERE",
    "<MAPS_SDK_ANDROID_KEY>",
    "CHANGEME",
}
SIGNING_SECRETS = (
    "LEOVER_STAGING_QA_KEYSTORE_B64",
    "LEOVER_STAGING_QA_STORE_PASSWORD",
    "LEOVER_STAGING_QA_KEY_PASSWORD",
    "LEOVER_STAGING_QA_KEY_ALIAS",
)


def env(name: str) -> str:
    return os.environ.get(name, "").strip()


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(1)


def require_signing_secrets() -> None:
    missing = [name for name in SIGNING_SECRETS if not env(name)]
    if missing:
        fail("missing signing secrets: " + ", ".join(missing))
    raw = "".join(env("LEOVER_STAGING_QA_KEYSTORE_B64").split())
    try:
        decoded = base64.b64decode(raw, validate=True)
    except Exception:
        fail("LEOVER_STAGING_QA_KEYSTORE_B64 is not valid Base64")
    if not decoded:
        fail("LEOVER_STAGING_QA_KEYSTORE_B64 decoded to an empty keystore")
    print("signing secrets present; values not printed")


def staging_client_key() -> str:
    key = env("SUPABASE_STAGING_PUBLISHABLE_KEY") or env("SUPABASE_STAGING_ANON_KEY")
    if not key:
        fail(
            "missing Supabase staging client key secret: "
            "SUPABASE_STAGING_PUBLISHABLE_KEY or SUPABASE_STAGING_ANON_KEY"
        )
    if "service_role" in key.lower():
        fail("service_role is forbidden in the staging client key")
    if any(char in key for char in "\r\n"):
        fail("staging client key contains a newline")
    return key


def maps_key() -> str:
    key = env("MAPS_API_KEY")
    if key in MAPS_SENTINELS or "MISSING" in key.upper() or len(key) < 30:
        fail("MAPS_API_KEY is blank or a sentinel")
    if any(char in key for char in "\r\n"):
        fail("MAPS_API_KEY contains a newline")
    return key


def staging_url() -> str:
    url = env("SUPABASE_STAGING_URL") or STAGING_URL
    if url.rstrip("/") != STAGING_URL:
        fail("SUPABASE_STAGING_URL must be the staging project")
    return STAGING_URL


def preserved_sdk_dir(path: Path) -> str:
    if not path.exists():
        return ""
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("sdk.dir=") and line.split("=", 1)[1].strip():
            return line
    return ""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--require-signing",
        action="store_true",
        help="Fail if the stable staging QA signing secrets are absent.",
    )
    args = parser.parse_args()
    if args.require_signing:
        require_signing_secrets()

    url = staging_url()
    client_key = staging_client_key()
    maps = maps_key()
    path = Path("local.properties")
    lines = []
    sdk = preserved_sdk_dir(path)
    if sdk:
        lines.append(sdk)
    lines.extend(
        [
            f"SUPABASE_STAGING_URL={url}",
            f"SUPABASE_STAGING_PUBLISHABLE_KEY={client_key}",
            f"MAPS_API_KEY={maps}",
        ]
    )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    path.chmod(stat.S_IRUSR | stat.S_IWUSR)
    print("local.properties written (gitignored); secret values not printed")
    print(f"supabase_target={url}")
    print(f"maps_key_length={len(maps)}")


if __name__ == "__main__":
    main()
