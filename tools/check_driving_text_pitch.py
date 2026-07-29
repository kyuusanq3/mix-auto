#!/usr/bin/env python3
"""Read-only check: driving style text-pitch-alignment under layout (not layer root).

Usage (repo root):
  powershell -NoProfile -ExecutionPolicy Bypass -File .\\scripts\\run-tool.ps1 check_driving_text_pitch
  # Linux/macOS: ./scripts/run-tool.sh check_driving_text_pitch

Exit 0 when every symbol+text-field has layout["text-pitch-alignment"]=="viewport"
and no layer has root-level text-pitch-alignment. Exit 1 otherwise.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STYLE = ROOT / "app" / "src" / "main" / "assets" / "map" / "mix-auto-driving.json"


def main() -> None:
    data = json.loads(STYLE.read_text(encoding="utf-8"))
    root_pitch = [layer.get("id", "?") for layer in data.get("layers", []) if "text-pitch-alignment" in layer]
    text_symbols = [
        layer
        for layer in data.get("layers", [])
        if layer.get("type") == "symbol" and "text-field" in layer.get("layout", {})
    ]
    missing = [
        layer.get("id", "?")
        for layer in text_symbols
        if layer.get("layout", {}).get("text-pitch-alignment") != "viewport"
    ]
    ok = len(text_symbols) - len(missing)

    print(
        f"text_symbols={len(text_symbols)} layout_viewport_ok={ok} "
        f"missing={len(missing)} root_pitch_keys={len(root_pitch)}"
    )
    if missing:
        print("missing_layout_pitch:", ",".join(missing[:10]), end="")
        if len(missing) > 10:
            print(f",...(+{len(missing) - 10} more)", end="")
        print()
    if root_pitch:
        print("root_pitch_layers:", ",".join(root_pitch[:10]), end="")
        if len(root_pitch) > 10:
            print(f",...(+{len(root_pitch) - 10} more)", end="")
        print()

    if missing or root_pitch:
        sys.exit(1)


if __name__ == "__main__":
    main()
