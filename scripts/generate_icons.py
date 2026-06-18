#!/usr/bin/env python3
"""Generate Android launcher icons from scripts/logo_clear.png (no Flutter)."""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw

BACKGROUND_COLOR_HEX = "#030000"
LOGO_OCCUPANCY_PERCENT = 70
ANDROID_LOGO_LETTERBOX_SCALE = 0.8
LAUNCHER_BG_HEX = "#0d0d0d"
SOURCE_LOGO_FILENAME = "logo_clear.png"

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parent
RES = PROJECT_ROOT / "app" / "src" / "main" / "res"
SOURCE_PATH = SCRIPT_DIR / SOURCE_LOGO_FILENAME
RESAMPLING = getattr(Image, "Resampling", Image).LANCZOS

MIPMAP_SIZES = {"mipmap-mdpi": 48, "mipmap-hdpi": 72, "mipmap-xhdpi": 96,
                "mipmap-xxhdpi": 144, "mipmap-xxxhdpi": 192}
FOREGROUND_SIZES = {"drawable-mdpi": 108, "drawable-hdpi": 162, "drawable-xhdpi": 216,
                    "drawable-xxhdpi": 324, "drawable-xxxhdpi": 432}


def _die(msg: str) -> None:
    print(msg, file=sys.stderr)
    raise SystemExit(1)


def _parse_hex_rgb(hex_color: str) -> tuple[int, int, int]:
    h = hex_color.strip().lstrip("#")
    if len(h) != 6 or not re.fullmatch(r"[0-9a-fA-F]{6}", h):
        _die(f"Invalid hex color: {hex_color!r}")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


@dataclass(frozen=True)
class IconRenderer:
    logo_rgba: Image.Image
    bg_rgb: tuple[int, int, int]
    occupancy: float

    @classmethod
    def from_source(cls, source: Path, bg_hex: str, occupancy_percent: float) -> IconRenderer:
        occ = max(1.0, min(100.0, float(occupancy_percent))) / 100.0
        return cls(logo_rgba=Image.open(source).convert("RGBA"), bg_rgb=_parse_hex_rgb(bg_hex), occupancy=occ)

    def _fit_logo(self, max_side: int) -> Image.Image:
        max_side = max(1, int(max_side))
        w, h = self.logo_rgba.size
        scale = min(max_side / w, max_side / h)
        return self.logo_rgba.resize((max(1, int(w * scale)), max(1, int(h * scale))), RESAMPLING)

    def render_square(self, size: int, *, letterbox_scale: float = 1.0, bg: tuple[int, int, int] | None = None) -> Image.Image:
        canvas = Image.new("RGBA", (size, size), (*(bg or self.bg_rgb), 255))
        inner = max(1, int(round(size * self.occupancy * letterbox_scale)))
        logo = self._fit_logo(inner)
        canvas.alpha_composite(logo, ((size - logo.width) // 2, (size - logo.height) // 2))
        return canvas

    def render_foreground(self, size: int) -> Image.Image:
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        inner = max(1, int(round(size * self.occupancy * ANDROID_LOGO_LETTERBOX_SCALE)))
        logo = self._fit_logo(inner)
        canvas.alpha_composite(logo, ((size - logo.width) // 2, (size - logo.height) // 2))
        return canvas

    def render_monochrome(self, size: int) -> Image.Image:
        logo = self._fit_logo(max(1, int(round(size * self.occupancy * ANDROID_LOGO_LETTERBOX_SCALE))))
        gray = logo.convert("L")
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        rgba = Image.merge("RGBA", (gray, gray, gray, logo.split()[3]))
        out.alpha_composite(rgba, ((size - logo.width) // 2, (size - logo.height) // 2))
        return out


def _save(path: Path, image: Image.Image, *, rgb: bool = False) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    im = image.convert("RGB" if rgb else "RGBA")
    im.save(path, format="PNG", optimize=True)


def main() -> None:
    if not SOURCE_PATH.is_file():
        _die(f"Source not found: {SOURCE_PATH}")

    renderer = IconRenderer.from_source(SOURCE_PATH, BACKGROUND_COLOR_HEX, LOGO_OCCUPANCY_PERCENT)
    lb = max(0.05, min(1.0, ANDROID_LOGO_LETTERBOX_SCALE))
    launcher_bg = _parse_hex_rgb(LAUNCHER_BG_HEX)

    for folder, size in MIPMAP_SIZES.items():
        img = renderer.render_square(size, letterbox_scale=lb, bg=launcher_bg)
        _save(RES / folder / "ic_launcher.png", img, rgb=True)
        _save(RES / folder / "ic_launcher_round.png", img, rgb=True)
        print(f"Wrote {folder}/ic_launcher.png ({size}px)")

    for folder, size in FOREGROUND_SIZES.items():
        _save(RES / folder / "ic_launcher_foreground.png", renderer.render_foreground(size))
        _save(RES / folder / "ic_launcher_monochrome.png", renderer.render_monochrome(size))
        print(f"Wrote {folder}/ic_launcher_foreground.png ({size}px)")

    _save(SCRIPT_DIR / "logo_android.png", renderer.render_square(1024, letterbox_scale=lb), rgb=True)
    print("Done.")


if __name__ == "__main__":
    main()
