#!/usr/bin/env python3
"""Compare static benchmark fixtures, allowing isolated rasterization rounding pixels."""
import argparse
from pathlib import Path
import numpy as np
from PIL import Image


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('before', type=Path)
    parser.add_argument('after', type=Path)
    args = parser.parse_args()
    count = 0
    failures = []
    for before in sorted((args.before / 'screenshots').glob('*.png')):
        if not any(name in before.name for name in (
            'programmable_block-', 'programmable_trigger_block-', 'programmable_slab-',
            'programmable_light-', 'programmable_porthole_'
        )):
            continue
        after = args.after / 'screenshots' / before.name
        a = np.asarray(Image.open(before).convert('RGB'), dtype=np.int16)
        b = np.asarray(Image.open(after).convert('RGB'), dtype=np.int16)
        if a.shape != b.shape:
            raise SystemExit(f'Image dimensions differ: {before.name}')
        diff = np.abs(a-b)
        fraction = np.any(diff > 3, axis=2).mean()
        if fraction > 0.0001:
            failures.append(f'{before.name}: {fraction:.3%} pixels differ by more than 3/255')
        count += 1
    if count != 30:
        raise SystemExit(f'Expected 30 static fixture images, found {count}')
    if failures:
        raise SystemExit('\n'.join(failures))
    print(f'PASS: {count} static benchmark images; at least 99.99% of pixels within 3/255 per channel')


if __name__ == '__main__':
    main()
