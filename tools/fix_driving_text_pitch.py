#!/usr/bin/env python3
"""Set layout text-pitch-alignment=viewport on driving-style text symbols.

Nav label stretch under tilted camera: MapLibre reads pitch from layer["layout"],
NOT the layer root. Use run-tool — never python -c or temp_fix.py.

Usage (repo root):
  powershell -NoProfile -ExecutionPolicy Bypass -File .\\scripts\\run-tool.ps1 fix_driving_text_pitch
  # Linux/macOS: ./scripts/run-tool.sh fix_driving_text_pitch
Then:
  .\\scripts\\verify-debug.ps1
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STYLE = ROOT / "app" / "src" / "main" / "assets" / "map" / "mix-auto-driving.json"


def main() -> None:
    data = json.loads(STYLE.read_text(encoding="utf-8"))
    updated = 0
    stripped_root = 0
    for layer in data.get("layers", []):
        if "text-pitch-alignment" in layer:
            layer.pop("text-pitch-alignment", None)
            stripped_root += 1
        if layer.get("type") != "symbol":
            continue
        layout = layer.setdefault("layout", {})
        if "text-field" not in layout:
            continue
        if layout.get("text-pitch-alignment") != "viewport":
            layout["text-pitch-alignment"] = "viewport"
            updated += 1

    # Keep one minified line (do not indent=2 — huge noisy diffs).
    STYLE.write_text(
        json.dumps(data, separators=(",", ":"), ensure_ascii=False) + "\n",
        encoding="utf-8",
    )

    text_symbols = [
        layer
        for layer in data.get("layers", [])
        if layer.get("type") == "symbol"
        and "text-field" in layer.get("layout", {})
    ]
    ok = sum(
        1
        for layer in text_symbols
        if layer.get("layout", {}).get("text-pitch-alignment") == "viewport"
    )
    print(
        f"updated={updated} stripped_root={stripped_root} "
        f"text_symbols={len(text_symbols)} layout_viewport_ok={ok}"
    )


if __name__ == "__main__":
    main()
