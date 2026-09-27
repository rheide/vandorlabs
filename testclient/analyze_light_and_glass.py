#!/usr/bin/env python3
"""Check distant joined-light artwork and continuous glass across joined panes."""
import sys
from pathlib import Path
import numpy as np
from PIL import Image
root = Path(sys.argv[1])
def pixels(name):
    return np.asarray(Image.open(root / ('shot_gallery_v12_' + name + '.png')).convert('RGB'), dtype=np.int16)
# The cyan center of a joined light must not contain housing-colored stripes.
for name, bounds in [('far', (657, 349, 662, 354)), ('oblique', (654, 341, 659, 346))]:
    x0, y0, x1, y1 = bounds
    face = pixels('light_depth_' + name)[y0:y1, x0:x1]
    assert np.all(face[:, :, 1] > 180), name + ': dark pixels through the light artwork'
    assert np.all(face[:, :, 1] - face[:, :, 0] > 30), name + ': housing color through the light artwork'
# Sky at the same scanline is the unglazed reference. These spans cross the
# joined opening, including its internal block seams, from both sides.
for name, bounds in [('front', (545, 670, 309)), ('back', (602, 739, 309)),
                     ('left', (588, 625, 312)), ('right', (608, 652, 312))]:
    x0, x1, y = bounds
    image = pixels('round_glass_' + name)
    difference = np.mean(np.abs(image[y, x0:x1] - image[y, 200]), axis=1)
    assert np.all(difference > 2), name + ': untinted gap in joined glass'
    assert np.all(difference < 25), name + ': opaque face inside the glass opening'
print('PASS: distant light faces stay clear; joined Circular glass is continuous in four views')
