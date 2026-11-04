#!/usr/bin/env python3
"""Encode native SegmentedDoorRenderChecks documentation frames as looping GIFs."""
import argparse
from pathlib import Path
from PIL import Image, ImageChops

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('capture', type=Path)
parser.add_argument('--output', type=Path, default=Path('docs/images'))
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
for motion in ('up', 'left'):
    paths = [args.capture / ('shot_segmented_animation_%s_%02d.png' % (motion, n)) for n in range(25)]
    frames = [Image.open(path).convert('RGB') for path in paths]
    assert all(frame.size == (400, 320) for frame in frames)
    assert frames[0].tobytes() != frames[-1].tobytes(), 'Motion capture must change'
    # Use one common crop for the whole sequence, preserving motion and camera scale.
    boxes = [ImageChops.difference(frame, Image.new('RGB', frame.size, (32, 48, 64))).getbbox() for frame in frames]
    crop = (max(0, min(b[0] for b in boxes)-16), max(0, min(b[1] for b in boxes)-16),
            min(400, max(b[2] for b in boxes)+16), min(320, max(b[3] for b in boxes)+16))
    frames = [frame.crop(crop) for frame in frames]
    loop = frames + frames[-2:0:-1]
    durations = [80] * len(loop)
    durations[0] = durations[24] = 700
    destination = args.output / ('segmented-2.8-%s.gif' % motion)
    loop[0].save(destination, save_all=True, append_images=loop[1:], duration=durations, loop=0, optimize=False)
    with Image.open(destination) as result:
        assert result.n_frames == 48
    print(destination)
