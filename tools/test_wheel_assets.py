"""Check all wheel model variants, including GUI projection with rotation and depth."""
import itertools
import json
import math
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "src/main/resources/assets/vandorlabs"
for size in ("small", "medium", "large"):
    name = "wheel_" + size
    model = json.loads((root / "models/block" / (name + ".json")).read_text())
    item = json.loads((root / "models/item" / (name + ".json")).read_text())
    variants = json.loads((root / "blockstates" / (name + ".json")).read_text())["variants"]
    assert len(variants) == 16, name
    gui = item["display"]["gui"]
    projected = []
    # Model GUI transforms use the model center; account for both rotation axes
    # and tyre depth, rather than comparing raw authored x/y dimensions.
    rx, ry, _ = map(math.radians, gui["rotation"])
    for element in model["elements"]:
        # Implicit model UVs use raw coordinates. Large wheels extend beyond
        # 16 units, so implicit UVs escape their stitched texture-atlas sprite.
        x0, y0, z0 = element["from"]
        x1, y1, z1 = element["to"]
        diameter = 32 if size == "large" else 16
        x0, x1, y0, y1 = (value * 16 / diameter for value in (x0, x1, y0, y1))
        expected_uvs = {
            "down": [x0, 16 - z1, x1, 16 - z0],
            "up": [x0, z0, x1, z1],
            "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0],
            "south": [x0, 16 - y1, x1, 16 - y0],
            "west": [z0, 16 - y1, z1, 16 - y0],
            "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
        }
        for face, expected in expected_uvs.items():
            actual = element["faces"][face]["uv"]
            assert len(actual) == 4 and all(0 <= value <= 16 for value in actual), (name, face, actual)
            assert actual[2] > actual[0] and actual[3] > actual[1], (name, face, actual)
            assert all(abs(a - b) < 1e-5 for a, b in zip(actual, expected)), (name, face, actual, expected)
            texture = element["faces"][face]["texture"]
            assert texture.startswith("#") and texture[1:] in model["textures"], (name, texture)
        for corner in itertools.product(*zip(element["from"], element["to"])):
            x, y, z = (v - 8 for v in corner)
            x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
            y = y * math.cos(rx) - z * math.sin(rx)
            projected.append((x * gui["scale"][0] + gui["translation"][0],
                              y * gui["scale"][1] + gui["translation"][1]))
    bounds = [min(p[axis] for p in projected) for axis in range(2)] + [
        max(p[axis] for p in projected) for axis in range(2)]
    assert all(abs(v) < 7 for v in bounds), (name, bounds)
    print(name, "PASS explicit in-sprite UVs and GUI bounds", [round(v, 2) for v in bounds])
print("PASS wheel variants retain at least one pixel of GUI padding on every side")
