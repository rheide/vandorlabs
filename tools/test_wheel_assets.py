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
        for corner in itertools.product(*zip(element["from"], element["to"])):
            x, y, z = (v - 8 for v in corner)
            x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
            y = y * math.cos(rx) - z * math.sin(rx)
            projected.append((x * gui["scale"][0] + gui["translation"][0],
                              y * gui["scale"][1] + gui["translation"][1]))
    bounds = [min(p[axis] for p in projected) for axis in range(2)] + [
        max(p[axis] for p in projected) for axis in range(2)]
    assert all(abs(v) < 7 for v in bounds), (name, bounds)
    print(name, "PASS GUI bounds", [round(v, 2) for v in bounds])
print("PASS wheel variants retain at least one pixel of GUI padding on every side")
