#!/usr/bin/env python3
"""Generate the low/high chair models and tile-driven block-state variants."""

import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/vandorlabs"
MODELS = ROOT / "models/block"
ITEMS = ROOT / "models/item/configured"
STYLES = ("command", "companion", "operator", "conference", "mess_hall")
FACINGS = (("south", 0), ("west", 90), ("north", 180), ("east", 270))


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n")


for style in STYLES:
    source = json.loads((MODELS / f"programmable_chair_{style}.json").read_text())
    for height, offset in (("low", -2), ("high", 2)):
        model = copy.deepcopy(source)
        for element in model["elements"]:
            name = element.get("name", "")
            if name in ("square_foot", "cross_base_x", "cross_base_z", "side_brace"):
                continue
            if name in ("pedestal", "leg"):
                element["to"][1] += offset
            else:
                element["from"][1] += offset
                element["to"][1] += offset
        write(MODELS / f"programmable_chair_{style}_{height}.json", model)
        write(ITEMS / f"programmable_chair_{style}_{height}.json", {
            "parent": f"vandorlabs:block/programmable_chair_{style}_{height}"
        })

variants = {}
for style in STYLES:
    for height in ("low", "middle", "high"):
        for facing, rotation in FACINGS:
            for upper in (False, True):
                key = f"facing={facing},height={height},style={style},upper={str(upper).lower()}"
                model = "programmable_chair_empty" if upper else f"programmable_chair_{style}"
                if height != "middle" and not upper:
                    model += f"_{height}"
                variant = {"model": f"vandorlabs:{model}"}
                if rotation:
                    variant["y"] = rotation
                variants[key] = variant
write(ROOT / "blockstates/programmable_chair.json", {"variants": variants})
