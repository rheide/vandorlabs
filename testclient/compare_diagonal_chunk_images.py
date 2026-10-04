#!/usr/bin/env python3
"""Compare old tile rendering and new chunk rendering under the same live lightmap."""
import argparse
from pathlib import Path
import numpy as np
from PIL import Image

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('run', type=Path)
args = parser.parse_args()
for variant in ('half', 'full', 'shallow', 'clipped'):
    stem = 'benchmark-vandorlabs-programmable_diagonal_wall-chunk_wall_' + variant
    root = args.run / 'screenshots'
    reference = np.asarray(Image.open(root / (stem + '_reference.png')).convert('RGB'), dtype=np.int16)
    chunk = np.asarray(Image.open(root / (stem + '.png')).convert('RGB'), dtype=np.int16)
    if reference.shape != chunk.shape:
        raise SystemExit(f'FAIL: {variant}: image dimensions differ')
    fraction = np.any(np.abs(reference - chunk) > 3, axis=2).mean()
    if fraction > 0.0001:
        raise SystemExit(f'FAIL: {variant}: {fraction:.3%} pixels differ by more than 3/255')
    print(f'PASS: {variant}: {1-fraction:.5%} of pixels within 3/255')
