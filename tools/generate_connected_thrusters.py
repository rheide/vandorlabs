#!/usr/bin/env python3
"""Generate clipped models and blockstates for connected square thrusters."""

import copy
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "generated-resources/assets/vandorlabs"
MODEL_DIR = ASSETS / "models/block"
STATE_DIR = ASSETS / "blockstates"
THRUSTERS = (
    "classic_rocket",
    "ion_drive",
    "plasma_vent_full_face",
    "impulse_engine_full_face",
)
MAX_SIZE = 8
ROTATIONS = {
    "north": {},
    "east": {"y": 90},
    "south": {"y": 180},
    "west": {"y": 270},
    "up": {"x": 270},
    "down": {"x": 90},
}


def clean_number(value):
    rounded = round(value, 6)
    return int(rounded) if rounded == int(rounded) else rounded


def lerp(start, end, amount):
    return start + (end - start) * amount


def part_name(size, x, y):
    if size == 2:
        if y == 0:
            return "bottom_left" if x == 0 else "bottom_right"
        return "top_left" if x == 0 else "top_right"
    return f"s{size}_x{x}_y{y}"


def parts():
    for size in range(2, MAX_SIZE + 1):
        for y in range(size):
            for x in range(size):
                yield part_name(size, x, y), size, x, y


def front_uv(uv, bounds, clipped):
    """Keep the emitter/cavity UV field continuous over the full assembly face."""
    x0, y0, x1, y1 = bounds
    cx0, cy0, cx1, cy1 = clipped
    u0, v0, u1, v1 = uv
    # On a north face Minecraft's U axis runs opposite model-space +X. A
    # geometrically left/west slice therefore needs the texture's right half.
    # Full models hid this because the emitter gradients are symmetric, but a
    # connected model otherwise exchanges the inner and outer color bands.
    fx0 = (x1 - cx1) / (x1 - x0)
    fx1 = (x1 - cx0) / (x1 - x0)
    fy0 = (y1 - cy1) / (y1 - y0)
    fy1 = (y1 - cy0) / (y1 - y0)
    return [clean_number(v) for v in (
        lerp(u0, u1, fx0), lerp(v0, v1, fy0),
        lerp(u0, u1, fx1), lerp(v0, v1, fy1),
    )]


def clipped_model(source, size, origin_x, origin_y):
    output = {key: copy.deepcopy(value) for key, value in source.items()
              if key != "elements"}
    # Minecraft shades each block independently. On horizontal assemblies that
    # makes otherwise continuous clipped faces acquire checkerboard lighting at
    # block boundaries, which reads as swapped texture quadrants. The complete
    # engine is one visual surface, so connected pieces use uniform face light.
    output["ambientocclusion"] = False
    output["elements"] = []
    clip_x0, clip_y0 = origin_x, origin_y
    clip_x1, clip_y1 = origin_x + 16.0, origin_y + 16.0
    epsilon = 1.0e-7
    for element in source["elements"]:
        if "rotation" in element:
            raise ValueError("connected thruster generator does not support rotated elements")
        original_from = element["from"]
        original_to = element["to"]
        x0, y0, z0 = original_from[0] * size, original_from[1] * size, original_from[2]
        x1, y1, z1 = original_to[0] * size, original_to[1] * size, original_to[2]
        cx0, cy0 = max(x0, clip_x0), max(y0, clip_y0)
        cx1, cy1 = min(x1, clip_x1), min(y1, clip_y1)
        if cx1 - cx0 <= epsilon or cy1 - cy0 <= epsilon or z1 - z0 <= epsilon:
            continue
        faces = {}
        for direction, face in element["faces"].items():
            if direction == "west" and abs(cx0 - x0) > epsilon:
                continue
            if direction == "east" and abs(cx1 - x1) > epsilon:
                continue
            if direction == "down" and abs(cy0 - y0) > epsilon:
                continue
            if direction == "up" and abs(cy1 - y1) > epsilon:
                continue
            generated_face = copy.deepcopy(face)
            # The north side is the authored engine opening. Split its UVs over
            # all assembly parts; other material surfaces repeat at native density.
            if direction == "north":
                generated_face["uv"] = front_uv(face["uv"], (x0, y0, x1, y1),
                                                  (cx0, cy0, cx1, cy1))
            faces[direction] = generated_face
        if not faces:
            continue
        generated = {key: copy.deepcopy(value) for key, value in element.items()
                     if key not in ("from", "to", "faces")}
        generated["from"] = [clean_number(cx0 - origin_x),
                             clean_number(cy0 - origin_y), clean_number(z0)]
        generated["to"] = [clean_number(cx1 - origin_x),
                           clean_number(cy1 - origin_y), clean_number(z1)]
        generated["faces"] = faces
        output["elements"].append(generated)
    return output


def blockstate(name):
    variants = {}
    for powered in (False, True):
        for particles in (False, True):
            mode = "stream" if powered and particles else "on" if powered else "off"
            for facing, rotation in ROTATIONS.items():
                for part in ("single",) + tuple(p[0] for p in parts()):
                    model = f"vandorlabs:{name}_{mode}"
                    if part != "single":
                        model += f"_{part}"
                    value = {"model": model}
                    value.update(rotation)
                    variants[(f"facing={facing},part={part},"
                              f"particles={str(particles).lower()},"
                              f"powered={str(powered).lower()}")] = value
    return {"variants": variants}


def validate_uv_axes():
    """Guard the face-local orientation that is easy to reverse when clipping."""
    uv = [0, 0, 16, 16]
    bounds = [0, 0, 32, 32]
    expected = {
        (0, 0, 16, 16): [8, 8, 16, 16],
        (16, 0, 32, 16): [0, 8, 8, 16],
        (0, 16, 16, 32): [8, 0, 16, 8],
        (16, 16, 32, 32): [0, 0, 8, 8],
    }
    for clipped, result in expected.items():
        actual = front_uv(uv, bounds, clipped)
        if actual != result:
            raise AssertionError(f"connected north-face UV {clipped}: {actual} != {result}")


def main():
    validate_uv_axes()
    generated = 0
    for name in THRUSTERS:
        for mode in ("off", "on", "stream"):
            source_path = MODEL_DIR / f"{name}_{mode}.json"
            source = json.loads(source_path.read_text())
            for part, size, x, y in parts():
                origin_x, origin_y = x * 16.0, y * 16.0
                target = MODEL_DIR / f"{name}_{mode}_{part}.json"
                target.write_text(json.dumps(clipped_model(source, size, origin_x, origin_y),
                                             indent=2) + "\n")
                generated += 1
        (STATE_DIR / f"{name}.json").write_text(
            json.dumps(blockstate(name), indent=2) + "\n")
    print(f"Generated {generated} connected-part models and {len(THRUSTERS)} blockstates")


if __name__ == "__main__":
    main()
