#!/usr/bin/env python3
"""Parse uiautomator XML dumps for Demar VPN ADB automation."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET

PACKAGE = "com.demar.vpn"


def parse_bounds(raw: str) -> tuple[int, int, int, int] | None:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", raw or "")
    if not m:
        return None
    return tuple(int(x) for x in m.groups())  # type: ignore[return-value]


def center(bounds: tuple[int, int, int, int]) -> tuple[int, int]:
    x1, y1, x2, y2 = bounds
    return (x1 + x2) // 2, (y1 + y2) // 2


def area(bounds: tuple[int, int, int, int]) -> int:
    x1, y1, x2, y2 = bounds
    return max(0, x2 - x1) * max(0, y2 - y1)


def walk(node: ET.Element, out: list[ET.Element]) -> None:
    out.append(node)
    for child in node:
        walk(child, out)


def screen_height(xml: str) -> int:
    m = re.search(r'bounds="\[0,0\]\[(\d+),(\d+)\]"', xml)
    return int(m.group(2)) if m else 2400


def cmd_connected(nodes: list[ET.Element]) -> None:
    texts = [n.attrib.get("text", "") for n in nodes]
    print("1" if "Подключено" in texts else "0")


def cmd_permission(nodes: list[ET.Element]) -> None:
    for node in nodes:
        text = (node.attrib.get("text") or "").lower()
        pkg = node.attrib.get("package", "")
        if pkg != "com.android.settings" and "vpn" not in text:
            continue
        bounds = parse_bounds(node.attrib.get("bounds", ""))
        if not bounds:
            continue
        if any(word in text for word in ("ok", "разреш", "allow", "подключ", "always")):
            cx, cy = center(bounds)
            print(f"{cx},{cy}")
            return
    print("")


def cmd_toggle(nodes: list[ET.Element], xml: str) -> None:
    height = screen_height(xml)
    best: tuple[int, int, str] | None = None
    best_area = -1
    for node in nodes:
        if node.attrib.get("package") != PACKAGE:
            continue
        if node.attrib.get("clickable") != "true":
            continue
        bounds = parse_bounds(node.attrib.get("bounds", ""))
        if not bounds:
            continue
        _, cy = center(bounds)
        if cy < height * 0.22 or cy > height * 0.72:
            continue
        w = bounds[2] - bounds[0]
        h = bounds[3] - bounds[1]
        if w < 180 or h < 180:
            continue
        score = area(bounds)
        if score > best_area:
            best_area = score
            cx, cy = center(bounds)
            best = (cx, cy, node.attrib.get("enabled", "true"))
    if best:
        print(f"{best[0]},{best[1]},{best[2]}")
    else:
        print("")


def main() -> None:
    if len(sys.argv) < 2:
        print("usage: adb_ui_parse.py <connected|permission|toggle>", file=sys.stderr)
        sys.exit(2)
    cmd = sys.argv[1]
    xml = sys.stdin.read()
    if not xml.strip():
        print("")
        return
    root = ET.fromstring(xml)
    nodes: list[ET.Element] = []
    walk(root, nodes)
    if cmd == "connected":
        cmd_connected(nodes)
    elif cmd == "permission":
        cmd_permission(nodes)
    elif cmd == "toggle":
        cmd_toggle(nodes, xml)
    else:
        print(f"unknown cmd: {cmd}", file=sys.stderr)
        sys.exit(2)


if __name__ == "__main__":
    main()
