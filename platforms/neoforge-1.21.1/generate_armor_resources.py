"""Package native armor from the same authored designs and recipes as Forge."""
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent / "build/generated/armor-resources"
LEGACY = ROOT / "src/main/resources/assets/vandorlabs"
ARTWORK = ROOT / "texture-packs/additional/assets/vandorlabs"
ASSETS = OUT / "assets/vandorlabs"
CATALOG = ROOT / "generated-resources/assets/vandorlabs/data/armor_textures.json"
PIECES = {"HEAD": "helmet", "CHEST": "chestplate", "LEGS": "leggings", "FEET": "boots"}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def copy(src, dst):
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, dst)


if OUT.exists():
    shutil.rmtree(OUT)
copy(CATALOG, ASSETS / "data/armor_textures.json")
designs = json.loads(CATALOG.read_text(encoding="utf-8"))
for design in designs:
    _, worn = design["worn"].split(":")
    _, icon = design["icon"].split(":")
    _, model = design["model"].split(":")
    copy(ARTWORK / worn, ASSETS / worn)
    copy(ARTWORK / f"textures/{icon}.png", ASSETS / f"textures/{icon.replace('items/', 'item/', 1)}.png")
    item_model = json.loads((LEGACY / f"models/item/{model}.json").read_text(encoding="utf-8"))
    item_model["textures"]["layer0"] = design["icon"].replace(":items/", ":item/", 1)
    write_json(ASSETS / f"models/item/{model}.json", item_model)

for slot, piece in PIECES.items():
    item = f"programmable_{piece}"
    design = next(d for d in designs if d["slot"] == slot and d["name"].startswith("civilian_staff_"))
    write_json(ASSETS / f"models/item/{item}.json",
               {"parent": design["model"].replace(":", ":item/", 1)})
    generic_model = json.loads((LEGACY / f"models/item/{item}.json").read_text(encoding="utf-8"))
    generic_model["textures"]["material"] = "minecraft:block/stone"
    write_json(ASSETS / f"models/item/armor/material_{piece}.json", generic_model)
    recipe = json.loads((LEGACY / f"recipes/{item}.json").read_text(encoding="utf-8"))
    recipe["result"] = {"id": recipe["result"]["item"]}
    write_json(OUT / f"data/vandorlabs/recipe/{item}.json", recipe)
