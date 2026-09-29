#!/usr/bin/env python3
"""Package the supplied mode artwork with the existing item's silhouette mask."""
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1]
artwork = Image.open(root / "img/copifier_multi_low.png").convert("RGBA")
mask = Image.open(root / "texture-packs/default/assets/vandorlabs/textures/items/duplifier_on.png")
assert artwork.size == mask.size, "Mode artwork must match the existing icon dimensions"
artwork.putalpha(mask.getchannel("A"))
for pack in ("default", "original"):
    artwork.save(root / f"texture-packs/{pack}/assets/vandorlabs/textures/items/duplifier_multi.png")
