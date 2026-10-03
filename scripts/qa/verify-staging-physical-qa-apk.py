#!/usr/bin/env python3
"""Verify the staging physical QA APK without printing client secrets.

Checks package, version, staging Supabase host, Maps manifest meta-data,
and the stable QA signing certificate.
"""

from __future__ import annotations

import hashlib
import os
import re
import subprocess
import sys
import zipfile
from pathlib import Path

EXPECTED_PACKAGE = "com.comunidapp.app.staging"
EXPECTED_VERSION_NAME = "1.1-staging"
EXPECTED_VERSION_CODE = "2"
EXPECTED_SHA1 = "92:D3:AF:F2:AC:CF:50:B8:68:15:95:26:4F:ED:C8:C2:DC:2B:E5:EF"
EXPECTED_SHA256 = (
    "07:FF:08:A5:B9:46:B3:59:A8:6F:B4:47:B4:9F:51:A6:"
    "08:C2:67:58:FF:ED:5B:48:96:8E:E9:3D:63:5A:AD:09"
)
STAGING_URL = "https://tobqbddfcyitwgbkthhy.supabase.co"
MAPS_SENTINELS = {"", "MAPS_API_KEY_MISSING", "YOUR_KEY_HERE", "<MAPS_SDK_ANDROID_KEY>"}


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(1)


def norm_fingerprint(value: str) -> str:
    compact = re.sub(r"[^0-9a-fA-F]", "", value)
    if len(compact) not in (40, 64):
        fail(f"unrecognized certificate fingerprint length {len(compact)}")
    return ":".join(compact[i : i + 2].upper() for i in range(0, len(compact), 2))


def sdk_roots() -> list[Path]:
    roots: list[Path] = []
    for name in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        raw = os.environ.get(name, "").strip()
        if raw:
            roots.append(Path(raw))
    roots.append(Path.home() / "Android" / "Sdk")
    roots.append(Path("/usr/local/lib/android/sdk"))
    props = Path("local.properties")
    if props.exists():
        for line in props.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                roots.append(Path(line.split("=", 1)[1].strip()))
    return roots


def find_tool(name: str) -> Path:
    found: list[Path] = []
    for root in sdk_roots():
        build_tools = root / "build-tools"
        if not build_tools.is_dir():
            continue
        found.extend(path for path in build_tools.glob(f"*/{name}") if path.is_file())
    if not found:
        fail(f"Android {name} was not found under the SDK build-tools")
    return sorted(found)[-1]


def run(cmd: list[str]) -> str:
    result = subprocess.run(cmd, check=False, capture_output=True, text=True)
    if result.returncode != 0:
        # Keep tool stderr only when it cannot contain the Maps key or client key.
        detail = (result.stderr or result.stdout or "").strip().splitlines()
        safe = detail[-1] if detail else f"exit {result.returncode}"
        fail(f"{Path(cmd[0]).name} failed: {safe}")
    return result.stdout


def apk_bytes(apk: Path) -> bytes:
    data = apk.read_bytes()
    if not data:
        fail("APK is empty")
    return data


def badging(aapt: Path, apk: Path) -> str:
    return run([str(aapt), "dump", "badging", str(apk)])


def manifest_tree(aapt: Path, apk: Path) -> str:
    return run([str(aapt), "dump", "xmltree", str(apk), "AndroidManifest.xml"])


def maps_meta_value(tree: str) -> str:
    match = re.search(
        r'com\.google\.android\.geo\.API_KEY.*?android:value(?:\([^)]*\))?="([^"]*)"',
        tree,
        re.DOTALL,
    )
    if not match:
        fail("Maps API_KEY meta-data is missing from the manifest")
    return match.group(1)


def jwt_roles(blob: bytes) -> set[str]:
    roles: set[str] = set()
    for token in re.findall(rb"eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}", blob):
        payload = token.split(b".")[1]
        pad = b"=" * ((4 - len(payload) % 4) % 4)
        try:
            import base64
            import json

            decoded = base64.urlsafe_b64decode(payload + pad)
            role = json.loads(decoded).get("role")
        except Exception:
            continue
        if isinstance(role, str):
            roles.add(role)
    return roles


def supabase_urls(blob: bytes) -> set[str]:
    return {item.decode("ascii") for item in re.findall(rb"https://[a-z0-9]+\.supabase\.co", blob)}


def dex_blob(apk: Path) -> bytes:
    chunks: list[bytes] = []
    with zipfile.ZipFile(apk) as archive:
        for info in archive.infolist():
            if info.filename.endswith(".dex"):
                chunks.append(archive.read(info))
    if not chunks:
        fail("APK contains no dex")
    return b"\n".join(chunks)


def signing_fingerprints(apksigner: Path, apk: Path) -> tuple[str, str, str]:
    text = run([str(apksigner), "verify", "-v", "--print-certs", str(apk)])
    sha1 = re.search(r"SHA-1 digest:\s*([0-9A-Fa-f:]+)", text)
    sha256 = re.search(r"SHA-256 digest:\s*([0-9A-Fa-f:]+)", text)
    if not sha1 or not sha256:
        fail("apksigner did not print certificate fingerprints")
    scheme = "v2" if re.search(r"v2 scheme[^\n]*true", text, re.IGNORECASE) else "unknown"
    return norm_fingerprint(sha1.group(1)), norm_fingerprint(sha256.group(1)), scheme


def keytool_fingerprints(apk: Path) -> tuple[str, str] | None:
    result = subprocess.run(
        ["keytool", "-printcert", "-jarfile", str(apk)],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return None
    sha1 = re.search(r"SHA1:\s*([0-9A-Fa-f:]+)", result.stdout)
    sha256 = re.search(r"SHA256:\s*([0-9A-Fa-f:]+)", result.stdout)
    if not sha1 or not sha256:
        return None
    return norm_fingerprint(sha1.group(1)), norm_fingerprint(sha256.group(1))


def git_sha() -> str:
    result = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return "unknown"
    return result.stdout.strip()


def emit(lines: list[str]) -> None:
    text = "\n".join(lines) + "\n"
    sys.stdout.write(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY", "").strip()
    if summary:
        with open(summary, "a", encoding="utf-8") as handle:
            handle.write("\n".join(["## Staging physical QA APK", "", *lines, ""]))


def main() -> None:
    if len(sys.argv) != 2:
        fail("usage: verify-staging-physical-qa-apk.py <apk>")
    apk = Path(sys.argv[1])
    if not apk.is_file():
        fail("APK path does not exist")

    data = apk_bytes(apk)
    aapt = find_tool("aapt")
    apksigner = find_tool("apksigner")
    badge = badging(aapt, apk)
    package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badge)
    if not package:
        fail("aapt badging did not include package identity")
    name, version_code, version_name = package.group(1), package.group(2), package.group(3)
    if name != EXPECTED_PACKAGE:
        fail(f"package mismatch: {name}")
    if version_name != EXPECTED_VERSION_NAME or version_code != EXPECTED_VERSION_CODE:
        fail(f"version mismatch: {version_name} ({version_code})")

    maps_value = maps_meta_value(manifest_tree(aapt, apk))
    if maps_value.strip() in MAPS_SENTINELS or "MISSING" in maps_value.upper() or len(maps_value.strip()) < 30:
        fail("Maps key in the manifest is blank or a sentinel")

    blob = dex_blob(apk)
    urls = supabase_urls(blob)
    if urls != {STAGING_URL}:
        fail("Supabase host packaged in dex is not staging-only: " + ", ".join(sorted(urls) or ["none"]))
    roles = jwt_roles(blob)
    if "service_role" in roles:
        fail("service_role credential is packaged in the APK")

    sha1, sha256, scheme = signing_fingerprints(apksigner, apk)
    if sha1 != EXPECTED_SHA1:
        fail("signing SHA1 mismatch")
    if sha256 != EXPECTED_SHA256:
        fail("signing SHA256 mismatch")
    keytool = keytool_fingerprints(apk)
    if keytool is not None and keytool != (sha1, sha256):
        fail("keytool certificate does not match apksigner")

    digest = hashlib.sha256(data).hexdigest()
    lines = [
        f"PACKAGE: {name}",
        f"VERSION_NAME: {version_name}",
        f"VERSION_CODE: {version_code}",
        f"APK_SIZE: {len(data)}",
        f"APK_SHA256: {digest}",
        f"SIGNING_SHA1: {sha1}",
        f"SIGNING_SHA256: {sha256}",
        "EXPECTED_SHA1_MATCH: YES",
        "EXPECTED_SHA256_MATCH: YES",
        f"MAPS_KEY_PACKAGED: YES",
        f"MAPS_KEY_LENGTH: {len(maps_value)}",
        "SUPABASE_TARGET: staging tobqbddfcyitwgbkthhy",
        f"JWT_ROLES: {', '.join(sorted(roles)) if roles else 'none'}",
        f"APK_SIGNATURE_SCHEME: {scheme}",
        f"SOURCE_GIT_SHA: {git_sha()}",
    ]
    emit(lines)


if __name__ == "__main__":
    main()
