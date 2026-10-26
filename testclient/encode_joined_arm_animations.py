#!/usr/bin/env python3
"""Validate and encode real joined-arm documentation captures."""
import argparse
import csv
from pathlib import Path
from PIL import Image, ImageChops

NAMES = ('extender-platform', 'sideways-extender-platform', 'rotating-platform')
# Fixed framing throughout each cycle keeps the complete mechanism in view.
CROPS = {
    'extender-platform': (128, 112, 640, 400),
    'sideways-extender-platform': (208, 72, 720, 360),
    'rotating-platform': (192, 112, 704, 400),
}


def encode(source, destination):
    rows = list(csv.DictReader((source / 'frames.tsv').open(), delimiter='\t'))
    assert len(rows) >= 40, f'{source.name}: incomplete capture'
    times = [float(row['elapsed_ms']) for row in rows]
    assert all(b > a for a, b in zip(times, times[1:])), 'Nonmonotonic capture clock'
    progress = [float(row['server_progress']) for row in rows]
    assert progress[0] == progress[-1] == 1 and min(progress) == 0, 'Missing full return cycle'
    bottom = progress.index(0)
    assert sum(0 < p < 1 for p in progress[:bottom]) >= 3, 'Missing real retraction poses'
    assert sum(0 < p < 1 for p in progress[bottom:]) >= 3, 'Missing real extension poses'
    frames = []
    for row in rows:
        with Image.open(source / row['file']) as image:
            assert image.width * 9 == image.height * 16, 'Expected a 16:9 capture'
            framed = image.convert('RGB').resize((896, 504), Image.Resampling.LANCZOS).crop(CROPS[source.name])
            frames.append(framed.resize((640, 360), Image.Resampling.LANCZOS))
    difference = ImageChops.difference(frames[0], frames[bottom])
    channels = difference.split()
    mask = ImageChops.lighter(ImageChops.lighter(channels[0], channels[1]), channels[2]).point(lambda v: 255 if v > 32 else 0)
    bounds = mask.getbbox()
    assert bounds and sum(mask.histogram()[1:]) > 300, 'No visible payload motion'
    assert bounds[0] > 2 and bounds[1] > 2 and bounds[2] < 638 and bounds[3] < 358, f'Motion clipped: {bounds}'
    training = Image.new('RGB', (160 * len(frames), 90))
    for i, frame in enumerate(frames):
        training.paste(frame.resize((160, 90), Image.Resampling.BOX), (i * 160, 0))
    palette = training.quantize(colors=256, method=Image.Quantize.MEDIANCUT)
    duration = int(times[-1] // 50 + 1) * 50
    chosen = [min(range(len(times)), key=lambda i: abs(times[i] - t)) for t in range(0, duration, 50)]
    indexed = [frames[i].quantize(palette=palette, dither=Image.Dither.NONE) for i in chosen]
    target = destination / (source.name + '.gif')
    indexed[0].save(target, save_all=True, append_images=indexed[1:], duration=50, loop=0, disposal=1, optimize=True)
    with Image.open(target) as gif:
        assert gif.is_animated and gif.info['loop'] == 0
        total = 0
        review = Image.new('RGB', (1280, 720))
        for i in range(gif.n_frames):
            gif.seek(i)
            total += gif.info['duration']
        assert total == duration, 'Encoded timing drift'
        for i, wanted in enumerate((0, duration * .22, duration * .42, duration * .58, duration * .75, duration - 1)):
            elapsed = 0
            for n in range(gif.n_frames):
                gif.seek(n)
                if elapsed + gif.info['duration'] > wanted:
                    review.paste(gif.convert('RGB').resize((426, 240)), ((i % 3) * 426, (i // 3) * 360))
                    break
                elapsed += gif.info['duration']
        review.save(source / 'review.png')
        print(f'{target}: {gif.n_frames} frames, {duration} ms, {target.stat().st_size} bytes, motion bounds {bounds}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('capture', type=Path)
    parser.add_argument('--destination', type=Path, default=Path('docs/images/joined-arms'))
    args = parser.parse_args()
    args.destination.mkdir(parents=True, exist_ok=True)
    for name in NAMES:
        encode(args.capture / name, args.destination)
