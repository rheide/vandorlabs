#!/usr/bin/env python3
"""Build the brighter particle-stream texture/model variants."""

import json
import math
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "generated-resources/assets/vandorlabs"
MODEL_DIR = ASSETS / "models/block"
STATE_DIR = ASSETS / "blockstates"
TEXTURE_ROOTS = (
    ROOT / "texture-packs/default/assets/vandorlabs/textures/blocks/thrusters",
    ROOT / "texture-packs/original/assets/vandorlabs/textures/blocks/thrusters",
)
SQUARES = {
    "rocket_thruster": "rocket",
    "ion_drive": "ion",
    "plasma_vent": "plasma",
    "impulse_engine": "impulse",
}
OBJ_BASES = (
    ("rocket_thruster_hexagonal", "rocket_thruster_wedge"),
    ("ion_drive_hexagonal", "ion_drive_wedge"),
    ("plasma_vent_hexagonal", "plasma_vent_wedge"),
    ("impulse_engine_hexagonal", "impulse_engine_wedge"),
)
OBJ_BLOCKS = tuple(block for hexagon, wedge in OBJ_BASES
                   for block in (hexagon,) + tuple(wedge + corner for corner in
                   ("", "_bottom_right", "_top_right", "_top_left")))
HOVER_BLOCKS = ("antigravity_plate", "repulsor_array", "vertical_hover_thruster")
ROTATIONS = {
    "north": {},
    "east": {"y": 90},
    "south": {"y": 180},
    "west": {"y": 270},
    "up": {"x": 270},
    "down": {"x": 90},
}


def family_for(name):
    if name.startswith("rocket_"):
        return "rocket"
    if name.startswith("ion_"):
        return "ion"
    if name.startswith("plasma_"):
        return "plasma"
    if name.startswith("impulse_"):
        return "impulse"
    raise ValueError(name)


def brighten(source, target):
    image = Image.open(source).convert("RGBA")
    output = Image.new("RGBA", image.size)
    source_pixels = image.load()
    target_pixels = output.load()
    for y in range(image.height):
        for x in range(image.width):
            red, green, blue, alpha = source_pixels[x, y]
            peak = max(red, green, blue)
            amount = 0.0 if peak <= 24 else 0.70 * ((peak - 24) / 231.0) ** 1.35
            target_pixels[x, y] = tuple(
                int(round(channel + (255 - channel) * amount))
                for channel in (red, green, blue)
            ) + (alpha,)
    output.save(target)


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n")


def inventory_obj(source, target):
    """Turn the complete thruster toward the inventory camera and keep it in-slot."""
    yaw = math.radians(195)
    pitch = math.radians(18)
    lines = []
    for line in source.read_text().splitlines():
        if line.startswith(("v ", "vn ")):
            kind, *values = line.split()
            x, y, z = (float(value) for value in values[:3])
            if kind == "v":
                x -= .5
                y -= .5
                z -= .5
            turned_x = math.cos(yaw) * x + math.sin(yaw) * z
            turned_z = -math.sin(yaw) * x + math.cos(yaw) * z
            turned_y = math.cos(pitch) * y - math.sin(pitch) * turned_z
            turned_z = math.sin(pitch) * y + math.cos(pitch) * turned_z
            if kind == "v":
                turned_x = .5 + .70 * turned_x
                turned_y = .5 + .70 * turned_y
                turned_z = .5 + .70 * turned_z
            line = f"{kind} {turned_x:.8f} {turned_y:.8f} {turned_z:.8f}"
        lines.append(line)
    target.write_text("\n".join(lines) + "\n")


def square_models():
    for block, family in SQUARES.items():
        source = json.loads((MODEL_DIR / f"{block}_on.json").read_text())
        old = f"vandorlabs:blocks/thrusters/{family}_on"
        new = f"vandorlabs:blocks/thrusters/{family}_stream"
        for key, value in tuple(source.get("textures", {}).items()):
            if value == old:
                source["textures"][key] = new
        write_json(MODEL_DIR / f"{block}_stream.json", source)


def obj_models_and_states():
    for block in OBJ_BLOCKS:
        family = family_for(block)
        source_obj = MODEL_DIR / f"{block}_on.obj"
        source_mtl = MODEL_DIR / f"{block}_on.mtl"
        target_obj = MODEL_DIR / f"{block}_stream.obj"
        target_mtl = MODEL_DIR / f"{block}_stream.mtl"
        target_obj.write_text(source_obj.read_text().replace(
                f"mtllib {block}_on.mtl", f"mtllib {block}_stream.mtl"))
        target_mtl.write_text(source_mtl.read_text().replace(
                f"blocks/thrusters/{family}_on",
                f"blocks/thrusters/{family}_stream"))
        inventory_obj(source_obj, MODEL_DIR / f"{block}_inventory.obj")

        old_state = json.loads((STATE_DIR / f"{block}.json").read_text())
        variants = {}
        for facing, rotation in ROTATIONS.items():
            for powered in (False, True):
                for particles in (False, True):
                    mode = "stream" if powered and particles else "on" if powered else "off"
                    value = {"model": f"vandorlabs:{block}_{mode}.obj"}
                    value.update(rotation)
                    variants[(f"facing={facing},particles={str(particles).lower()},"
                              f"powered={str(powered).lower()}")] = value
        # The nozzle is on the local north face. Turn it toward the GUI camera
        # so both the illuminated opening and the housing depth remain visible.
        variants["inventory"] = [{
            "model": f"vandorlabs:{block}_inventory.obj",
            "y": 0,
            "transform": "forge:default-block",
        }]
        old_state["variants"] = variants
        write_json(STATE_DIR / f"{block}.json", old_state)


def hover_states():
    for block in HOVER_BLOCKS:
        path = STATE_DIR / f"{block}.json"
        state = json.loads(path.read_text())
        base_variants = {}
        for key, value in state["variants"].items():
            fields = [field for field in key.split(",")
                      if not field.startswith("particles=")]
            base_variants[",".join(fields)] = value
        variants = {}
        for key, value in base_variants.items():
            for particles in (False, True):
                fields = key.split(",")
                fields.insert(1, f"particles={str(particles).lower()}")
                variants[",".join(fields)] = value
        state["variants"] = variants
        write_json(path, state)


def main():
    for root in TEXTURE_ROOTS:
        for family in SQUARES.values():
            brighten(root / f"{family}_on.png", root / f"{family}_stream.png")
    square_models()
    obj_models_and_states()
    hover_states()
    print("Generated 8 stream textures, 4 JSON models, 40 OBJ/MTL files, "
          "20 inventory OBJ models, 20 OBJ blockstates and 3 hover blockstates")


if __name__ == "__main__":
    main()
