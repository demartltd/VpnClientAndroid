#!/usr/bin/env python3
"""Remove sing-box artifacts installed by libbox-build-android.py."""

from __future__ import annotations

import argparse
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
ANDROID_ASSETS_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "singbox"
LIBBOX_AAR = REPO_ROOT / "app" / "libs" / "libbox.aar"

_ARTIFACTS: tuple[str, ...] = ("bundled_version.txt",)


def _artifact_paths() -> list[Path]:
    paths = [LIBBOX_AAR]
    for name in _ARTIFACTS:
        paths.append(ANDROID_ASSETS_DIR / name)
    return paths


def main() -> None:
    parser = argparse.ArgumentParser(description="Delete sing-box files installed by build scripts.")
    parser.add_argument("-y", "--yes", action="store_true", help="Do not ask for confirmation.")
    parser.add_argument("--dry-run", action="store_true", help="Print paths only.")
    args = parser.parse_args()

    existing = [p for p in _artifact_paths() if p.is_file()]
    if not existing:
        print("Nothing to remove.")
        return

    print("Will remove:")
    for p in existing:
        print(f"  {p.relative_to(REPO_ROOT)}")

    if args.dry_run:
        return
    if not args.yes:
        reply = input("Proceed? [y/N]: ").strip().lower()
        if reply not in ("y", "yes"):
            print("Aborted.")
            return

    for p in existing:
        p.unlink()
        print(f"Removed: {p.relative_to(REPO_ROOT)}")
    print("Done.")


if __name__ == "__main__":
    main()
