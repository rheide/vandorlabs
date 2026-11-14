#!/usr/bin/env python3
"""Author the compact rack and its padded cassette item from exact model-unit boxes.

The SOUTH-facing front has a one-unit rim and a three-unit recessed control
fascia. Runtime and Dynmap consume these same JSON parts; no OBJ loader or
per-frame geometry generation is required. --check verifies reproducibility.
"""
import argparse
import io
import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/vandorlabs"
MODELS = ASSETS / "models/block/module_rack"
TEXTURES = ROOT / "texture-packs/additional/assets/vandorlabs/textures/blocks/module_rack"
OUTPUT = {}
FACES = ("down", "up", "north", "south", "west", "east")
PALETTE = {
    "housing": (37, 41, 45), "edge": (57, 63, 69),
    "module": (27, 31, 35), "trim": (85, 95, 103),
    "dark": (12, 15, 18), "amber": (198, 138, 55),
    "cyan": (63, 226, 239),
}


def save_json(path, value):
    OUTPUT[path] = (json.dumps(value, indent=2) + "\n").encode()


def save_png(name, image):
    stream = io.BytesIO()
    image.save(stream, format="PNG", optimize=False, compress_level=9)
    OUTPUT[TEXTURES / (name + ".png")] = stream.getvalue()


def textures():
    image = Image.new("RGB", (64, 64), PALETTE["housing"])
    draw = ImageDraw.Draw(image)
    # Broad quiet pixel panels, not random texture noise or sub-pixel bevels.
    draw.rectangle((0, 0, 63, 63), outline=PALETTE["edge"], width=2)
    draw.rectangle((6, 6, 57, 57), outline=(30, 34, 38), width=2)
    for box in ((8, 8, 21, 9), (42, 54, 55, 55), (8, 40, 9, 49)):
        draw.rectangle(box, fill=(44, 49, 54))
    save_png("housing", image)

    image = Image.new("RGB", (64, 64), PALETTE["module"])
    draw = ImageDraw.Draw(image)
    # Top: 46 x 50 texels = 11.5 x 12.5 model units at four texels/unit.
    draw.rectangle((0, 0, 45, 49), outline=(48, 54, 60), width=1)
    for y in (7, 11, 38, 42):
        draw.rectangle((13, y, 32, y + 1), fill=(14, 18, 22))
    for x, y in ((3, 3), (40, 3), (3, 45), (40, 45)):
        draw.rectangle((x, y, x + 2, y + 1), fill=(67, 74, 80))
    draw.rectangle((12, 21, 14, 32), fill=PALETTE["amber"])
    draw.rectangle((31, 21, 33, 32), fill=PALETTE["amber"])
    draw.rectangle((12, 30, 33, 32), fill=PALETTE["amber"])
    draw.rectangle((18, 21, 27, 26), fill=(31, 182, 203))
    draw.line((18, 21, 27, 21), fill=PALETTE["cyan"])
    # Front: 46 x 12 texels, with blank end zones behind the vertical pulls.
    draw.rectangle((0, 52, 45, 63), fill=(23, 27, 31))
    draw.line((1, 52, 44, 52), fill=(45, 52, 58))
    draw.rectangle((18, 55, 27, 56), fill=(39, 190, 208))
    draw.rectangle((20, 59, 25, 60), fill=PALETTE["amber"])
    # A side strip with restrained rail/seam detail.
    draw.rectangle((48, 0, 59, 49), fill=(31, 35, 39))
    draw.line((50, 1, 50, 48), fill=(47, 54, 61))
    draw.line((57, 1, 57, 48), fill=(16, 20, 24))
    save_png("module", image)

    image = Image.new("RGB", (16, 16), PALETTE["trim"])
    draw = ImageDraw.Draw(image)
    draw.line((0, 0, 15, 0), fill=(115, 124, 130), width=2)
    draw.line((0, 14, 15, 14), fill=(61, 68, 74), width=2)
    save_png("trim", image)
    image = Image.new("RGB", (16, 16), PALETTE["dark"])
    ImageDraw.Draw(image).rectangle((1, 1, 14, 14), outline=(17, 21, 25))
    save_png("dark", image)

    image = Image.new("RGB", (16, 16), (19, 23, 26))
    draw = ImageDraw.Draw(image)
    # Left eight texels: unpopulated. Right eight: populated, including paired bars.
    for x, cyan, amber in ((0, (22, 67, 74), (73, 56, 34)),
                           (8, (83, 247, 255), (239, 177, 74))):
        draw.rectangle((x, 0, x + 7, 7), outline=(50, 58, 64))
        draw.rectangle((x + 1, 1, x + 6, 2), fill=cyan)
        draw.rectangle((x + 1, 4, x + 6, 5), fill=amber)
        draw.line((x + 1, 7, x + 6, 7), fill=(10, 13, 16))
    save_png("indicators", image)

    image = Image.new("RGB", (16, 16), (29, 24, 15))
    draw = ImageDraw.Draw(image)
    for x in range(1, 15, 3):
        draw.rectangle((x, 1, x + 1, 14), fill=PALETTE["amber"])
        draw.line((x, 1, x + 1, 1), fill=(239, 193, 104))
    save_png("contacts", image)


def box(name, low, high, texture, uv=None, cull=(), face_textures=None, shade=True):
    faces = {}
    for face in FACES:
        value = {"texture": "#" + (face_textures or {}).get(face, texture)}
        if uv is not None:
            value["uv"] = uv.get(face, uv.get("all", [0, 0, 16, 16])) if isinstance(uv, dict) else uv
        if face in cull:
            value["cullface"] = face
        faces[face] = value
    return {"name": name, "from": low, "to": high, "shade": shade, "faces": faces}


def model(elements):
    return {"ambientocclusion": False,
            "textures": {**{name: "vandorlabs:blocks/module_rack/" + name
                            for name in ("housing", "module", "trim", "dark", "indicators", "contacts")},
                         "particle": "vandorlabs:blocks/module_rack/housing"},
            "elements": elements}


def shell():
    parts = [
        box("bottom_rim_1px", [0, 0, 0], [16, 1, 16], "housing", cull=("down", "west", "east", "north")),
        box("top_rim_1px", [0, 15, 0], [16, 16, 16], "housing", cull=("up", "west", "east", "north")),
        box("left_rim_1px", [0, 1, 0], [1, 15, 16], "housing", cull=("west", "north")),
        box("back", [1, 1, 0], [13, 15, 1], "dark", cull=("north",)),
        box("fascia_3px_recessed_1px", [13, 1, 0], [16, 15, 15], "housing", cull=("east", "north")),
    ]
    for y in (4.25, 7.75, 11.25):
        parts.append(box("separator", [1, y, 1], [13, y + .5, 16], "housing"))
    for slot in range(4):
        y = 11.75 - slot * 3.5
        parts.append(box("button_socket_%s" % slot, [13.375, y + .75, 15], [15.625, y + 2.25, 15.125], "dark"))
    return parts


def empty_bay(slot):
    y = 11.75 - slot * 3.5
    parts = [box("slide_rail_%s" % slot, [x, y, 1], [x + .75, y + .25, 14.5], "trim")
             for x in (1.5, 11.75)]
    parts.append(box("rear_connector_%s" % slot, [5.5, y + .875, 1], [8.5, y + 2.125, 1.25], "contacts", [0, 0, 16, 8]))
    return parts


def cassette(bottom):
    uv = {"up": [0, 0, 11.5, 12.5], "down": [12, 0, 15, 12.5],
          "south": [0, 13, 11.5, 16], "north": [12, 0, 15, 3],
          "east": [12, 0, 15, 12.5], "west": [12, 0, 15, 12.5]}
    parts = [box("cassette_body", [1.25, bottom, 2.75], [12.75, bottom + 3, 15.25], "module", uv)]
    for x in (1.5, 12):
        # Vertical grip plus upper/lower standoffs: open finger space, never outside the row.
        parts.append(box("vertical_handle", [x, bottom + .375, 15.75], [x + .5, bottom + 2.625, 16], "trim", [0, 0, 4, 16]))
        for y in (bottom + .375, bottom + 2.25):
            parts.append(box("handle_standoff", [x, y, 15.25], [x + .5, y + .375, 15.75], "trim", [0, 0, 8, 8]))
    for x in (1.25, 12.5):
        parts.append(box("cassette_guide", [x, bottom + .25, 2.75], [x + .25, bottom + .5, 15.25], "trim", [0, 0, 4, 16]))
    for x in (4.25, 7.25):
        parts.append(box("keyed_contacts", [x, bottom + 1.25, 2.25], [x + 2.5, bottom + 1.75, 2.75], "contacts", [0, 0, 16, 8]))
    return parts


def seated_cassette(slot):
    bottom = 11.75 - slot * 3.5
    parts = cassette(bottom)
    result = []
    # The seated face closes the slot. Its black inset seals the fine clearance
    # gaps, so unseen full-depth circuitry, rear contacts and rails can be omitted.
    low = 1 if slot == 3 else bottom
    high = 15 if slot == 0 else bottom + 3
    seal = box("seated_slot_seal", [1, low, 15], [13, high, 15], "dark")
    seal["faces"] = {"south": seal["faces"]["south"]}
    result.append(seal)
    for part in parts:
        if part["name"] in ("cassette_guide", "keyed_contacts"):
            continue
        if part["name"] == "cassette_body":
            part["from"][2] = 15
            del part["faces"]["north"]
            for face in ("up", "down", "west", "east"):
                part["faces"][face]["texture"] = "#trim"
                part["faces"][face]["uv"] = [0, 0, 16, 2]
        elif part["name"] == "handle_standoff":
            # Both ends meet the solid front and vertical grip.
            del part["faces"]["north"]
            del part["faces"]["south"]
        result.append(part)
    return result


def indicator(slot, on):
    y = 11.75 - slot * 3.5
    parts = [box("slot_%d_indicator_%s" % (slot, "on" if on else "off"),
                 [13.5, y + .875, 15.125], [15.5, y + 2.125, 15.25], "indicators",
                 [8 if on else 0, 0, 16 if on else 8, 8], shade=not on)]
    return model(parts)


def item_display(module=False):
    scale = .66 if module else .56
    return {"gui": {"rotation": [25, -35, 0], "translation": [0, 0, 0], "scale": [scale] * 3},
            "ground": {"translation": [0, 2, 0], "scale": [.25] * 3},
            "fixed": {"scale": [.6] * 3},
            "firstperson_righthand": {"rotation": [0, -35, 0], "translation": [0, 0, 0], "scale": [.4] * 3},
            "firstperson_lefthand": {"rotation": [0, 35, 0], "translation": [0, 0, 0], "scale": [.4] * 3},
            "thirdperson_righthand": {"rotation": [75, -35, 0], "translation": [0, 2.5, 0], "scale": [.375] * 3},
            "thirdperson_lefthand": {"rotation": [75, 35, 0], "translation": [0, 2.5, 0], "scale": [.375] * 3}}


def generate():
    textures()
    shell_model = model(shell())
    save_json(MODELS / "shell.json", shell_model)
    for slot in range(4):
        save_json(MODELS / ("slot_%d.json" % slot), model(cassette(11.75 - slot * 3.5)))
        save_json(MODELS / ("slot_seated_%d.json" % slot), model(seated_cassette(slot)))
        save_json(MODELS / ("slot_empty_%d.json" % slot), model(empty_bay(slot)))
        for on in (False, True):
            save_json(MODELS / ("indicator_%d_%s.json" % (slot, "on" if on else "off")), indicator(slot, on))
    blank = model(cassette(6.5))
    blank["display"] = item_display(True)
    save_json(MODELS / "blank_module.json", blank)
    # Item uses empty bays with four unlit slot buttons; world gets stateful parts at bake.
    item = model(shell() + [p for s in range(4) for p in empty_bay(s)]
                 + [p for s in range(4) for p in indicator(s, False)["elements"]])
    item["display"] = item_display()
    save_json(ASSETS / "models/item/module_rack.json", item)
    save_json(ASSETS / "models/item/blank_module.json", {"parent": "vandorlabs:block/module_rack/blank_module"})
    save_json(ASSETS / "blockstates/module_rack.json", {"variants": {
        "facing=" + face: {"model": "vandorlabs:module_rack/shell", "x": x, "y": y}
        for face, x, y in (("south", 0, 0), ("west", 0, 90), ("north", 0, 180),
                           ("east", 0, 270), ("up", 90, 0), ("down", 270, 0))}})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    generate()
    stale = []
    for path, data in OUTPUT.items():
        if args.check:
            if not path.is_file() or path.read_bytes() != data:
                stale.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    if stale:
        raise SystemExit("Stale module-rack assets:\n" + "\n".join(stale))
    print(("Checked" if args.check else "Generated") + " %d module-rack assets" % len(OUTPUT))


if __name__ == "__main__":
    main()
