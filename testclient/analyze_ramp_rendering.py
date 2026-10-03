#!/usr/bin/env python3
"""Check source artwork on an actual deployed platform, away from static fixtures."""
import sys
from pathlib import Path
from PIL import Image

root = Path(sys.argv[1])
image = Image.open(root / 'shot_controller_2_open.png').convert('RGB')
assert image.size == (1280, 720), 'ramp capture dimensions changed'
# This region contains the moving brick platform, excluding the stationary
# source platform and redstone block. Corrupt UV/color packing renders blue/
# black strips here instead of brick. The reported broken build has zero hits.
region = image.crop((590, 440, 780, 615))
pixels = region.tobytes()
brick = sum(1 for r, g, b in zip(pixels[0::3], pixels[1::3], pixels[2::3])
            if r > 40 and r > 1.25 * g and r > 1.15 * b)
assert brick > 1000, f'deployed platform lost brick artwork: {brick} pixels'
print(f'PASS: deployed ramp source artwork ({brick} brick pixels)')
