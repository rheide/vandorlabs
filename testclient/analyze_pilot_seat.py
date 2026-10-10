#!/usr/bin/env python3
"""Check the rendered Pilot Seat icon has padding inside its inventory slot."""
import sys
from pathlib import Path
import numpy as np
from PIL import Image

image = np.asarray(Image.open(Path(sys.argv[1]) / 'shot_pilot_seat_icons.png').convert('RGB'), dtype=np.int16)
assert image.shape == (720, 1280, 3), 'Unexpected test viewport'
# Auto GUI scale 3: the test screen is 427 x 240 logical pixels.
x, y, scale = (427 // 2 - 8) * 3, (240 // 2) * 3, 3
left, top = x - 8 * scale, y - 8 * scale
region = image[top:y + 24 * scale, left:x + 24 * scale]
backgrounds = ((0x25, 0x34, 0x41), (0x60, 0x70, 0x80), (0x10, 0x18, 0x20))
mask = np.ones(region.shape[:2], dtype=bool)
for color in backgrounds:
    mask &= np.max(np.abs(region - color), axis=2) > 2
rows, cols = np.nonzero(mask)
assert len(rows) > 40, 'Pilot Seat icon is missing'
cols, rows = cols + left - x, rows + top - y
assert cols.min() >= scale and cols.max() < 15 * scale, 'Icon lacks horizontal padding'
assert rows.min() >= scale and rows.max() < 15 * scale, 'Icon lacks vertical padding'
print('PASS: Pilot Seat rendered icon has padding on every side')
