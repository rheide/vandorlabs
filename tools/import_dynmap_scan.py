#!/usr/bin/env python3
"""Import DynmapBlockScan output and repair unsupported Forge OBJ thrusters.

DynmapBlockScan 3.7 understands Vandor Labs' ordinary JSON models, but its
1.12 parser cannot read Forge OBJ models.  This keeps the scanner's complete,
state-aware output and substitutes compact Dynmap-native geometry for the hex
and triangular propulsion fixtures that the scanner otherwise emits empty.
"""

import argparse
import re
from pathlib import Path


THRUSTER = re.compile(
    r"^%(?P<id>(?P<family>rocket_thruster|ion_drive|plasma_vent|"
    r"impulse_engine)_(?P<shape>hexagonal|wedge)(?P<corner>_bottom_right|"
    r"_top_right|_top_left)?)$"
)

REMOVED_DOORS = {"door_airlock_glass", "door_security", "sliding_airlock_glass", "sliding_security_door"}
REMOVED_DOORS.update(f"detail_{family}_{motion}_{size}"
                     for family in ("split", "observation", "engineering")
                     for motion in ("sliding", "rotating") for size in ("single", "double"))
FALLBACK_IDS = {"programmable_half_input", "programmable_input",
                "programmable_diagonal_screen"}
FALLBACK_IDS.update("bridge_chair_" + role for role in
                    ("mess_hall", "conference", "command", "operator", "companion"))

EXTRA_TEXTURES = {}


def removed_record(line):
    block = re.match(r"(?:modellist|block):id=%([^,]+),", line)
    if block and block[1] in REMOVED_DOORS:
        return True
    return line.startswith("texture:") and (
        "/textures/blocks/detailed_doors/" in line or
        any("/" + name in line for name in REMOVED_DOORS))



def parse_record(line):
    match = re.match(r"^(modellist|block):id=([^,]+),state=([^,]+)(.*)$", line)
    if not match:
        return None
    state = dict(part.split(":", 1) for part in match.group(3).split("/"))
    return match, THRUSTER.match(match.group(2)), state


def texture_name(family, state):
    prefix = {
        "rocket_thruster": "rocket",
        "ion_drive": "ion",
        "plasma_vent": "plasma",
        "impulse_engine": "impulse",
    }[family]
    if state["powered"] != "true":
        return prefix + "_off"
    return prefix + ("_stream" if state["particles"] == "true" else "_on")


def rotation(facing):
    return {
        "north": "",
        "east": ":R/0/90/0",
        "south": ":R/0/180/0",
        "west": ":R/0/270/0",
        "up": ":R/270/0/0",
        "down": ":R/90/0/0",
    }[facing]


def textured_box(bounds, facing):
    return (",box=" + bounds
            + ":e/0:u/0:n/2:s/1:d/0:w/0" + rotation(facing))


def plain_box(bounds, facing):
    return (",box=" + bounds
            + ":e/0:u/0:n/0:s/0:d/0:w/0" + rotation(facing))


def model_suffix(shape, corner, facing):
    if shape == "hexagonal":
        # Two overlapping shallow boxes form a chamfered, full-face fixture.
        return (textured_box("2/0/0:14/16/3", facing)
                + textured_box("0/2/0:16/14/3", facing))

    # Eight strips approximate the authored right triangle while retaining the
    # correct empty half of the block.  The unsuffixed variant is bottom-left.
    corner = corner or "_bottom_left"
    pieces = []
    for row in range(8):
        y0 = row * 2
        y1 = y0 + 2
        width = 16 - row * 2
        if corner.startswith("_top"):
            y0, y1 = 16 - y1, 16 - y0
        if corner.endswith("right"):
            x0, x1 = 16 - width, 16
        else:
            x0, x1 = 0, width
        pieces.append(textured_box(
            "%d/%d/0:%d/%d/3" % (x0, y0, x1, y1), facing))
    return "".join(pieces)


def fallback_texture(block_id, state):
    if block_id.startswith("bridge_chair_"):
        return "bridge_chair_atlas"
    if block_id.startswith("programmable_"):
        return "dark_wall_panel"
    raise ValueError("No fallback texture for " + block_id)



def fallback_model(block_id, state):
    facing = state.get("facing", "north")
    if block_id.startswith("bridge_chair_"):
        return (plain_box("3/0/3:13/1/13", facing)
                + plain_box("6/1/6:10/7/10", facing)
                + plain_box("2/7/3:14/10/13", facing)
                + plain_box("2/10/11:14/16/14", facing))
    if block_id.startswith("programmable_"):
        if block_id == "programmable_diagonal_screen":
            return (plain_box("0/0/8:16/4/16", facing)
                    + plain_box("0/4/10:16/8/16", facing)
                    + plain_box("0/8/12:16/12/16", facing)
                    + plain_box("0/12/14:16/16/16", facing))
        return plain_box("0/0/0:16/3/16", facing)
    raise ValueError("No fallback model for " + block_id)


def repair_models(text):
    repaired = 0
    result = []
    for line in text.splitlines():
        if removed_record(line): continue
        # DynmapBlockScan 3.7 emits explicit per-face UV bounds that Dynmap's
        # Forge 1.12 loader rejects as invalid patches for many otherwise-valid
        # JSON boxes.  Omitting them asks the same loader to derive safe UVs
        # from each box while preserving geometry, state and texture slots.
        line = re.sub(r"([eunsdw]/\d+)(?:/-?\d+(?:\.\d+)?){4}", r"\1", line)
        record = parse_record(line)
        if record and record[0].group(1) == "modellist" and not record[0].group(4):
            match, thruster, state = record
            if thruster:
                line += model_suffix(thruster.group("shape"),
                                     thruster.group("corner"), state["facing"])
                repaired += 1
            elif match.group(2)[1:] in FALLBACK_IDS:
                line += fallback_model(match.group(2)[1:], state)
                repaired += 1
        result.append(line)
    return "\n".join(result) + "\n", repaired


def repair_textures(text):
    repaired = 0
    result = []
    for index, line in enumerate(text.splitlines()):
        if removed_record(line): continue
        if index == 0:
            result.append(line)
            for texture_id, relative in sorted(EXTRA_TEXTURES.items()):
                result.append("texture:id=%s,filename=assets/vandorlabs/textures/%s,xcount=1,ycount=1"
                              % (texture_id, relative))
            continue
        record = parse_record(line)
        if record and record[0].group(1) == "block" and "patch0=" not in line:
            match, thruster, state = record
            if thruster:
                emitter = texture_name(thruster.group("family"), state)
                line += ",patch0=0:side,patch1=0:rear,patch2=0:" + emitter
                repaired += 1
            elif match.group(2)[1:] in FALLBACK_IDS:
                line += ",patch0=0:" + fallback_texture(match.group(2)[1:], state)
                repaired += 1
        result.append(line)
    return "\n".join(result) + "\n", repaired


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("scan_dir", type=Path,
                        help="Dynmap renderdata/modsupport directory")
    parser.add_argument("--output", type=Path,
                        default=Path("src/main/resources/assets/vandorlabs"))
    args = parser.parse_args()

    source_models = args.scan_dir / "vandorlabs-models.txt"
    source_textures = args.scan_dir / "vandorlabs-texture.txt"
    models, model_count = repair_models(source_models.read_text())
    textures, texture_count = repair_textures(source_textures.read_text())
    if model_count != 1244 or texture_count != 1244:
        raise SystemExit("expected 1244 unsupported states, repaired models=%d textures=%d"
                         % (model_count, texture_count))

    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "dynmap-models.txt").write_text(models)
    (args.output / "dynmap-texture.txt").write_text(textures)
    print("Imported Dynmap support; repaired %d unsupported model states" % model_count)


if __name__ == "__main__":
    main()
