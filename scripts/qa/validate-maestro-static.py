#!/usr/bin/env python3
"""Static Maestro checks. Does not start an emulator or run a flow."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAESTRO = ROOT / ".maestro"
FLOWS = MAESTRO / "flows"
PROD_REF = "wystsapjfpdtoprlmizz"
SECRET_MARKERS = (
    "service_role",
    "SUPABASE_ACCESS_TOKEN",
    "BEGIN PRIVATE KEY",
    "eyJ",
)


def flow_files() -> list[Path]:
    return sorted(FLOWS.glob("*.yaml")) + sorted((MAESTRO / "subflows").glob("*.yaml"))


def referenced_files(text: str, source: Path) -> list[tuple[str, Path]]:
    found = []
    for match in re.finditer(r"^\s+file:\s+(\S+)\s*$", text, re.M):
        raw = match.group(1)
        found.append((raw, (source.parent / raw).resolve()))
    for match in re.finditer(r'^\s+-\s+"(\.\./fixtures/[^"]+)"\s*$', text, re.M):
        raw = match.group(1)
        found.append((raw, (source.parent / raw).resolve()))
    return found


def main() -> int:
    errors: list[str] = []
    flows = flow_files()
    if not flows:
        print("NO_FLOWS")
        return 1
    for path in flows:
        text = path.read_text(encoding="utf-8")
        if not text.startswith("appId:"):
            errors.append(f"{path.name}: missing appId")
        if "\n---\n" not in text:
            errors.append(f"{path.name}: missing document separator")
        lower = text.lower()
        if PROD_REF in text or "production.supabase" in lower:
            errors.append(f"{path.name}: production URL")
        for marker in SECRET_MARKERS:
            if marker.lower() in lower or marker in text:
                errors.append(f"{path.name}: secret marker {marker}")
        for raw, target in referenced_files(text, path):
            if not target.exists():
                errors.append(f"{path.name}: missing {raw}")
    print(f"FLOWS={len(flows)}")
    print(f"ERRORS={len(errors)}")
    for error in errors:
        print(error)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
