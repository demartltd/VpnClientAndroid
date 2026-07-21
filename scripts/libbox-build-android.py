#!/usr/bin/env python3
"""Build minimal Libbox AAR for Android (ShadowTLS + Hysteria2/QUIC + gVisor TUN, arm64-only).

Sync TAGS / MAX_AAR_BYTES with demar_vpn/scripts/libbox-build-android.py.
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = REPO_ROOT / "app" / "libs"
OUT_AAR = OUT_DIR / "libbox.aar"
ASSETS_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "singbox"
SINGBOX_API = "https://api.github.com/repos/SagerNet/sing-box/releases/latest"
MAX_AAR_BYTES = 40 * 1024 * 1024
TAGS = "with_gvisor,with_quic,with_clash_api,badlinkname,tfogo_checklinkname0"


def latest_tag() -> str:
    req = urllib.request.Request(SINGBOX_API, headers={"Accept": "application/vnd.github+json"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        tag = str(json.load(resp).get("tag_name", "")).strip()
    if not tag:
        sys.exit("No tag_name in latest sing-box release.")
    return tag


def main() -> None:
    if shutil.which("go") is None:
        sys.exit("go is required to build Libbox AAR")

    tag = latest_tag()
    print(f"Building minimal Libbox {tag} (arm64, tags={TAGS}) …")
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="demar-libbox-android-") as tmp:
        src = Path(tmp) / "sing-box"
        subprocess.run(
            [
                "git", "clone", "--depth", "1", "--branch", tag,
                "https://github.com/SagerNet/sing-box.git", str(src),
            ],
            check=True,
        )
        gopath = subprocess.check_output(["go", "env", "GOPATH"], text=True).strip()
        env = {**os.environ, "PATH": f"{gopath}/bin:{os.environ.get('PATH', '')}"}
        for pkg in (
            "github.com/sagernet/gomobile/cmd/gomobile@v0.1.12",
            "github.com/sagernet/gomobile/cmd/gobind@v0.1.12",
        ):
            subprocess.run(["go", "install", "-v", pkg], cwd=src, env=env, check=True)
        gomobile = Path(gopath) / "bin" / "gomobile"
        subprocess.run([str(gomobile), "init"], cwd=src, env=env, check=True)

        ldflags = (
            f"-X github.com/sagernet/sing-box/constant.Version={tag} "
            "-X internal/godebug.defaultGODEBUG=multipathtcp=0 "
            "-s -w -buildid= -checklinkname=0"
        )
        subprocess.run(
            [
                str(gomobile), "bind",
                "-v",
                "-trimpath",
                "-buildvcs=false",
                "-ldflags", ldflags,
                "-target", "android/arm64",
                "-androidapi", "23",
                "-javapkg=io.nekohasekai",
                "-libname=box",
                "-tags", TAGS,
                "-o", "libbox.aar",
                "./experimental/libbox",
            ],
            cwd=src,
            env=env,
            check=True,
        )
        built = src / "libbox.aar"
        if not built.is_file():
            sys.exit("libbox.aar was not produced.")
        size = built.stat().st_size
        if size > MAX_AAR_BYTES:
            sys.exit(
                f"libbox.aar is {size / 1024 / 1024:.1f} MiB "
                f"(limit {MAX_AAR_BYTES / 1024 / 1024:.0f} MiB); review build tags."
            )
        shutil.copy2(built, OUT_AAR)
        ASSETS_DIR.mkdir(parents=True, exist_ok=True)
        (ASSETS_DIR / "bundled_version.txt").write_text(f"{tag}\n", encoding="utf-8")
    print(f"Done: {OUT_AAR.relative_to(REPO_ROOT)} ({size / 1024 / 1024:.1f} MiB)")


if __name__ == "__main__":
    main()
