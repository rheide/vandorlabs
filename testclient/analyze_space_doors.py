#!/usr/bin/env python3
"""Catch missing Space sprites and frozen door animation in live screenshots."""
import sys
from pathlib import Path
from PIL import Image, ImageChops, ImageStat

root = Path(sys.argv[1])
for motion in ('sliding', 'rotating'):
    for trim in ('bare', 'framed'):
        shots = []
        for pose in ('closed', 'open'):
            name = f'gallery_space_{motion}_{trim}_{pose}'
            image = Image.open(root / f'shot_{name}.png').convert('RGB')
            magenta = sum(r > 80 and b > 80 and g < min(r, b) * .3
                          for r, g, b in getattr(image, 'get_flattened_data', image.getdata)())
            assert magenta < 20, f'{name}: missing-texture magenta ({magenta} pixels)'
            shots.append(image)
        difference = sum(ImageStat.Stat(ImageChops.difference(*shots)).mean) / 3
        assert difference > .1, f'{motion}/{trim}: open and closed look identical'
Image.open(root / 'shot_gallery_space_glass.png').verify()
log = (root / 'client.log').read_text()
assert 'FML.TEXTURE_ERRORS' not in log, 'Client reported texture loading errors'
assert 'Exception baking model' not in log, 'Client reported model baking errors'
print('PASS: all Space door galleries have textures and distinct open/closed poses')
