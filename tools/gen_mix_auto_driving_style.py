#!/usr/bin/env python3
"""Regenerate app/src/main/assets/map/mix-auto-driving.json from OpenFreeMap Liberty."""

from __future__ import annotations

import json
import sys
import urllib.request
from pathlib import Path

LIBERTY_URL = "https://tiles.openfreemap.org/styles/liberty"
OUT_PATH = Path(__file__).resolve().parents[1] / "app/src/main/assets/map/mix-auto-driving.json"
MAIN_ROAD_FACTOR = 3.0
MINOR_ROAD_FACTOR = 3.4
# MapLibreEngineImpl.applyAutomotiveRoadBoost() applies AUTOMOTIVE_*_ROAD_EXTRA on top at style load.
LABEL_FACTOR = 1.35
LABEL_MIN_ZOOM = 16


def boost_interpolate(obj, factor: float, min_zoom: float = 0):
    if not isinstance(obj, list):
        return obj
    if len(obj) >= 4 and obj[0] == "interpolate" and obj[2] == ["zoom"]:
        out = [obj[0], obj[1], obj[2]]
        i = 3
        while i < len(obj):
            if (
                i + 1 < len(obj)
                and isinstance(obj[i], (int, float))
                and isinstance(obj[i + 1], (int, float))
            ):
                z, value = obj[i], obj[i + 1]
                if value > 0 and z >= min_zoom:
                    out.extend([z, value * factor])
                else:
                    out.extend([z, value])
                i += 2
            else:
                out.append(boost_interpolate(obj[i], factor, min_zoom))
                i += 1
        return out
    return [boost_interpolate(item, factor, min_zoom) for item in obj]


def is_road_line_layer(layer_id: str) -> bool:
    if not (
        layer_id.startswith("road_")
        or layer_id.startswith("tunnel_")
        or layer_id.startswith("bridge_")
    ):
        return False
    if "one_way" in layer_id or layer_id.startswith("road_area"):
        return False
    if "path" in layer_id or "pedestrian" in layer_id or "rail" in layer_id:
        return False
    return True


def road_factor(layer_id: str) -> float:
    if any(
        token in layer_id
        for token in ("minor", "service", "track", "tertiary", "living", "street", "link")
    ):
        return MINOR_ROAD_FACTOR
    return MAIN_ROAD_FACTOR


def main() -> int:
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    if source:
        style = json.loads(source.read_text(encoding="utf-8"))
    else:
        request = urllib.request.Request(
            LIBERTY_URL,
            headers={"User-Agent": "MixAutoCarLauncher/1.0"},
        )
        with urllib.request.urlopen(request, timeout=120) as response:
            style = json.load(response)

    style["name"] = "Mix Auto Driving"

    for layer in style.get("layers", []):
        layer_id = layer.get("id", "")
        layer_type = layer.get("type")
        if layer_type == "line" and is_road_line_layer(layer_id):
            paint = layer.setdefault("paint", {})
            if "line-width" in paint:
                paint["line-width"] = boost_interpolate(
                    paint["line-width"],
                    road_factor(layer_id),
                )
        if layer_id in ("highway-name-minor", "highway-name-major"):
            layout = layer.setdefault("layout", {})
            if "text-size" in layout:
                layout["text-size"] = boost_interpolate(
                    layout["text-size"],
                    LABEL_FACTOR,
                    LABEL_MIN_ZOOM,
                )

    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUT_PATH.write_text(json.dumps(style, separators=(",", ":")), encoding="utf-8")
    print(f"Wrote {OUT_PATH} ({OUT_PATH.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
