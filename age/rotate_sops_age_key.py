#!/usr/bin/env python3
"""Simpler SOPS age rotation helper.

Creates a new age identity, updates the public recipient in .sops.yaml,
and runs `sops updatekeys` on files found under `k8s/` and `k8s_backup/`.

Run with: `python3 age/rotate_sops_age_key.py`
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path


def check_command(name: str) -> None:
    if shutil.which(name) is None:
        print(f"error: required command not found: {name}", file=sys.stderr)
        sys.exit(1)


def main() -> None:
    parser = argparse.ArgumentParser(description="Rotate SOPS age key (simpler Python version)")
    # No --key-file option: the script uses the default key location below.
    parser.parse_args()

    repo_root = Path(__file__).resolve().parent.parent
    config_file = repo_root / ".sops.yaml"
    key_file_value = os.environ.get("SOPS_AGE_KEY_FILE")
    if not key_file_value:
        print(
            "error: SOPS_AGE_KEY_FILE must be set to the age identity file path",
            file=sys.stderr,
        )
        sys.exit(1)

    key_file = Path(key_file_value).expanduser()

    for cmd in ("age-keygen", "sops"):
        check_command(cmd)

    if not config_file.exists():
        print(f"error: missing SOPS config: {config_file}", file=sys.stderr)
        sys.exit(1)

    key_file.parent.mkdir(parents=True, exist_ok=True)
    # Generate a new identity and append it to the key file pointed to by
    # SOPS_AGE_KEY_FILE. This matches `age-keygen >> "$SOPS_AGE_KEY_FILE"`.
    old_umask = os.umask(0o077)
    try:
        result = subprocess.run(
            ["age-keygen"],
            check=True,
            text=True,
            capture_output=True,
        )
        with key_file.open("a", encoding="utf-8") as key_handle:
            key_handle.write(result.stdout)
    finally:
        os.umask(old_umask)

    try:
        key_file.chmod(0o600)
    except Exception:
        pass

    match = re.search(r"(?m)^#\s*public key:\s*(age1[0-9a-z]+)\s*$", result.stdout)
    if not match:
        print("error: could not parse public key from age-keygen output", file=sys.stderr)
        sys.exit(1)
    new_recipient = match.group(1)

    text = config_file.read_text()
    updated, count = re.subn(
        r'(?m)^\s*age:\s*age1[0-9a-z]+\s*$',
        f'    age: {new_recipient}',
        text,
        count=1,
    )
    if count != 1:
        print("error: could not find an age recipient line in .sops.yaml", file=sys.stderr)
        sys.exit(1)
    config_file.write_text(updated)

    secret_files: list[Path] = []
    for base in (repo_root / "k8s", repo_root / "k8s_backup"):
        if base.exists():
            secret_files.extend(sorted(base.rglob("*.sops.yaml")))
            secret_files.extend(sorted(base.rglob("*.sops.yml")))

    # Deduplicate and sort
    secret_files = sorted(dict.fromkeys(secret_files))

    if not secret_files:
        print("warning: no SOPS files found under k8s or k8s_backup", file=sys.stderr)
        sys.exit(0)

    sops_env = os.environ.copy()
    sops_env["SOPS_AGE_KEY_FILE"] = str(key_file)

    for sf in secret_files:
        subprocess.run(["sops", "updatekeys", "--yes", str(sf)], check=True, env=sops_env)

    print("Rotated SOPS age key successfully.")
    print(f"New key file: {key_file}")
    print(f"New recipient: {new_recipient}")
    print(f"Re-encrypted files: {len(secret_files)}")


if __name__ == "__main__":
    main()
