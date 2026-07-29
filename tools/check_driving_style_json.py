#!/usr/bin/env python3
"""Read-only check: mix-auto-driving.json parses and stays minified (one line).

Usage (repo root):
  powershell -NoProfile -ExecutionPolicy Bypass -File .\\scripts\\run-tool.ps1 check_driving_style_json
  # Linux/macOS: ./scripts/run-tool.sh check_driving_style_json

Exit 0 when JSON is valid and effectively one-line minified. Exit 1 on parse error
or when line count > 1 (pretty-print / indent=2 accident).
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STYLE = ROOT / "app" / "src" / "main" / "assets" / "map" / "mix-auto-driving.json"


def main() -> None:
    text = STYLE.read_text(encoding="utf-8")
    lines = text.splitlines()
    line_count = len(lines) if text else 0

    try:
        data = json.loads(text)
    except json.JSONDecodeError as exc:
        print(f"invalid_json line={exc.lineno} col={exc.colno} msg={exc.msg}")
        sys.exit(1)

    layer_count = len(data.get("layers", []))
    print(f"line_count={line_count} layer_count={layer_count} path={STYLE.name}")

    if line_count > 1:
        print("error: expected minified one-line JSON (indent=2 / pretty-print detected)")
        sys.exit(1)


if __name__ == "__main__":
    main()
