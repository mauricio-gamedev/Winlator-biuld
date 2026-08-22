#!/usr/bin/env python3
"""Validate Winlator Build component and profile metadata against repository schemas."""

from __future__ import annotations

import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[1]

TARGETS = (
    (ROOT / "components" / "component.schema.json", ROOT / "components" / "manifests"),
    (ROOT / "profiles" / "device-profile.schema.json", ROOT / "profiles" / "devices"),
    (ROOT / "profiles" / "game-profile.schema.json", ROOT / "profiles" / "games"),
)


def load_json(path: Path) -> object:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def validate_group(schema_path: Path, data_dir: Path) -> int:
    schema = load_json(schema_path)
    validator = Draft202012Validator(schema)
    failures = 0

    if not data_dir.exists():
        print(f"[skip] {data_dir.relative_to(ROOT)} does not exist yet")
        return 0

    files = sorted(data_dir.glob("*.json"))
    if not files:
        print(f"[skip] no metadata files in {data_dir.relative_to(ROOT)}")
        return 0

    for path in files:
        data = load_json(path)
        errors = sorted(validator.iter_errors(data), key=lambda error: list(error.path))

        if not errors:
            print(f"[ok] {path.relative_to(ROOT)}")
            continue

        failures += 1
        print(f"[fail] {path.relative_to(ROOT)}")
        for error in errors:
            location = ".".join(str(part) for part in error.absolute_path) or "<root>"
            print(f"  - {location}: {error.message}")

    return failures


def main() -> int:
    failures = 0

    for schema_path, data_dir in TARGETS:
        failures += validate_group(schema_path, data_dir)

    if failures:
        print(f"\nMetadata validation failed for {failures} file(s).")
        return 1

    print("\nMetadata validation passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
