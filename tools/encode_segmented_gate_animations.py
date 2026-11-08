#!/usr/bin/env python3
"""Encode the installed 2.9 gate documentation fixture; require complete native frame coverage."""
import argparse
from pathlib import Path
from PIL import Image, ImageChops

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('capture', type=Path)
p.add_argument('--output', type=Path, default=Path('docs/images'))
a = p.parse_args()
a.output.mkdir(parents=True, exist_ok=True)
for style in ('picket', 'industrial', 'lattice'):
    for motion in ('slide', 'bend'):
        frames = [Image.open(a.capture / f'shot_gate_{style}_{motion}_{n:02}.png').convert('RGB') for n in range(25)]
        assert all(f.size == (480, 300) for f in frames)
        assert frames[0].tobytes() != frames[-1].tobytes(), 'Stationary gate capture'
        boxes = [ImageChops.difference(f, Image.new('RGB', f.size, (32, 48, 64))).getbbox() for f in frames]
        assert all(boxes), 'Empty gate frame'
        crop = (max(0, min(b[0] for b in boxes)-12), max(0, min(b[1] for b in boxes)-12),
                min(480, max(b[2] for b in boxes)+12), min(300, max(b[3] for b in boxes)+12))
        frames = [f.crop(crop) for f in frames]
        loop = frames + frames[-2:0:-1]
        durations = [80] * len(loop)
        durations[0] = durations[24] = 700
        destination = a.output / f'segmented-2.9-{style}-{motion}.gif'
        loop[0].save(destination, save_all=True, append_images=loop[1:], duration=durations, loop=0, optimize=False)
        with Image.open(destination) as check:
            assert check.n_frames == 48
        print(destination)
