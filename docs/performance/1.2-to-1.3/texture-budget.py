"""Estimate custom sprite pixel storage from the standard build's resources.

This is payload accounting, not a measurement of atlas dimensions or VRAM.
Run from the repository root after Gradle classes/processResources.
"""
import json
import struct
from pathlib import Path

root = Path("build/resources/main/assets/vandorlabs/textures/blocks")
catalog = json.loads(Path("generated-resources/assets/vandorlabs/data/unified_textures.json").read_text())
pixels = count = 0


def dimensions(source):
    return struct.unpack(">II", (root / (source + ".png")).read_bytes()[16:24])


def account(width, height):
    global pixels, count
    canvas = 1 << (max(width, height) - 1).bit_length()
    pixels += canvas * canvas
    count += 1


for entry in catalog:
    if "rectangular" not in entry and "top" not in entry:
        continue
    width, height = dimensions(entry["source"])
    if "crop" in entry:
        width -= width // 16
        height -= 2 * (height // 32)
    account(width, height)
    if "design" in entry:
        account(width, height // 2)
    if "top" in entry:
        for face in ("top", "side"):
            account(*dimensions(entry[face]))

print(f"Catalog extras: {len(catalog)}; custom Sprite entries: {count}")
print(f"Padded pixels: {pixels}; RGBA base payload: {pixels * 4 / 2**20:.3f} MiB")
print(f"RGBA payload with full mip-chain approximation: {pixels * 16 / 3 / 2**20:.3f} MiB")
