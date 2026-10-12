#!/usr/bin/env python3
"""Check the rendered Pilot Seat icon has padding inside its inventory slot."""
import sys
from pathlib import Path
from PIL import Image

image = Image.open(Path(sys.argv[1]) / 'shot_pilot_seat_icons.png').convert('RGB')
assert image.size == (1280, 720), 'Unexpected test viewport'
# Auto GUI scale 3: the test screen is 427 x 240 logical pixels.
x, y, scale = (427 // 2 - 8) * 3, (240 // 2) * 3, 3
left, top = x - 8 * scale, y - 8 * scale
region = image.crop((left, top, x + 24 * scale, y + 24 * scale))
backgrounds = ((0x25, 0x34, 0x41), (0x60, 0x70, 0x80), (0x10, 0x18, 0x20))
points = [(cx + left - x, cy + top - y)
          for cy in range(region.height) for cx in range(region.width)
          if all(max(abs(a - b) for a, b in zip(region.getpixel((cx, cy)), color)) > 2
                 for color in backgrounds)]
assert len(points) > 40, 'Pilot Seat icon is missing'
cols, rows = zip(*points)
assert min(cols) >= scale and max(cols) < 15 * scale, 'Icon lacks horizontal padding'
assert min(rows) >= scale and max(rows) < 15 * scale, 'Icon lacks vertical padding'
print('PASS: Pilot Seat rendered icon has padding on every side')
