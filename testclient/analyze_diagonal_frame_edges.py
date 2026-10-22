#!/usr/bin/env python3
"""Require visible cyan bottom-rail artwork with a floor and solid side neighbours."""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'build/tooling/python-packages'))
from PIL import Image


def check(directory):
    for state in ('dry', 'wet'):
        path = directory / ('diagonal-glass-bottom-frame-' + state + '.png')
        image = Image.open(path).convert('RGB')
        if image.size != (640, 420):
            raise AssertionError('Unexpected contact capture dimensions: ' + str(path))
        # Central bottom border only: exclude both side rails and the top rail.
        # Cyan has comparable green/blue, unlike the blue water behind the glass.
        pixels = image.crop((220, 210, 300, 280))
        cyan = sum(g > r * 1.3 and b > r * 1.3 and g > 70 and .75 * g < b < 1.3 * g
                   for r, g, b in pixels.getdata())
        if cyan < 12:
            raise AssertionError(f'{state}: bottom frame artwork missing/occluded ({cyan} cyan pixels): {path}')
        print(f'PASS: {state} diagonal bottom frame remains visible beside floor/side blocks ({cyan} cyan pixels)')


if __name__ == '__main__':
    check(Path(sys.argv[1]))
