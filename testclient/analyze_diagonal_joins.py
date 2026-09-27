#!/usr/bin/env python3
"""Compare identical shallow porthole fixtures with joining enabled/disabled."""
import sys
from pathlib import Path
import numpy as np
from PIL import Image

root = Path(sys.argv[1])
assert 'diagonal-placement-joins PASS' in (root / 'client.log').read_text()
def pixels(suffix):
    return np.asarray(Image.open(root / ('shot_gallery_v12_portholes_half_height' + suffix + '.png')).convert('RGB'), dtype=np.int16)
joined, separate = pixels(''), pixels('_unjoined')
# Identical camera and fixtures: joined openings must visibly remove the dividing frames.
changed = np.max(np.abs(joined - separate), axis=2) > 20
# Restrict comparisons to the four fixtures, excluding sky/cloud motion.
for left, right in [(395, 495), (500, 610), (625, 750), (780, 885)]:
    assert np.count_nonzero(changed[290:440, left:right]) > 30, (
        'A shallow porthole opening did not visibly join', left, right)

print('PASS: diagonal placement/joins in four directions and all shapes; shallow Join On/Off changes rendered openings')
