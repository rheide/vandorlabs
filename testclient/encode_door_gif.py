#!/usr/bin/env python3
"""Encode real ReproLab door frames with one palette and recorded timing."""
import argparse
import csv
from pathlib import Path
from PIL import Image, ImageChops


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('capture', type=Path)
    parser.add_argument('--output', type=Path, default=Path(
        'docs/images/gallery/doors/observation-rotating.gif'))
    args = parser.parse_args()
    rows = list(csv.DictReader((args.capture / 'door-frames.tsv').open(), delimiter='\t'))
    assert len(rows) >= 20, 'Too few real frames'
    assert rows[0]['open'] == rows[-1]['open'] == 'false'
    assert any(row['open'] == 'true' for row in rows), 'Door never opened'
    times = [float(row['elapsed_ms']) for row in rows]
    assert all(b > a for a, b in zip(times, times[1:])), 'Invalid frame clock'
    frames = []
    for row in rows:
        with Image.open(args.capture / row['file']) as image:
            assert image.size == (1280, 720)
            frames.append(image.convert('RGB').crop((350, 150, 950, 650)))
    # Intermediate geometry must differ from both settled endpoints; this
    # rejects a GIF made only from closed/open stills or background flicker.
    def changed(a, b):
        red, green, blue = ImageChops.difference(a, b).split()
        maximum = ImageChops.lighter(ImageChops.lighter(red, green), blue)
        return sum(maximum.histogram()[25:])
    closed = frames[0]
    open_start = next(i for i, row in enumerate(rows) if row['open'] == 'true')
    close_start = next(i for i in range(open_start + 1, len(rows)) if rows[i]['open'] == 'false')
    opened = frames[close_start - 1]
    assert changed(closed, opened) > 5000, 'Door silhouettes did not change'
    # Anchor motion windows to observed network updates, allowing ordinary
    # server/client synchronization delay and slow screenshot writes.
    for start in (open_start, close_start):
        low, high = times[start], times[start] + 450
        assert sum(changed(frame, closed) > 1000 and changed(frame, opened) > 1000
                   for frame, t in zip(frames, times) if low <= t <= high) >= 2, 'Missing intermediate motion'
    assert changed(closed, frames[-1]) < 1000, 'Cycle did not return to closed'
    # Build a shared palette from evenly sized samples of every captured pose.
    strip = Image.new('RGB', (120 * len(frames), 100))
    for i, frame in enumerate(frames):
        strip.paste(frame.resize((120, 100), Image.Resampling.BOX), (120 * i, 0))
    palette = strip.quantize(colors=256, method=Image.Quantize.MEDIANCUT)
    indexed = [frame.quantize(palette=palette, dither=Image.Dither.NONE) for frame in frames]
    ticks = [round(t / 10) * 10 for t in times] + [3400]
    durations = [max(10, b - a) for a, b in zip(ticks, ticks[1:])]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    indexed[0].save(args.output, save_all=True, append_images=indexed[1:],
                    duration=durations, loop=0, disposal=1, optimize=True)
    with Image.open(args.output) as gif:
        assert gif.is_animated and gif.n_frames >= 12 and gif.info['loop'] == 0
        assert gif.size == (600, 500)
        total = 0
        for i in range(gif.n_frames):
            gif.seek(i)
            total += gif.info['duration']
        assert abs(total - 3400) <= 30
        print(f'PASS: {gif.n_frames} encoded frames, {total}ms loop, 600x500, '
              f'{args.output.stat().st_size} bytes; real opening and closing poses verified')
    # Optional review artifact stays with disposable captures, outside docs.
    review = Image.new('RGB', (300 * 4, 250 * 2))
    for i, target in enumerate((0, times[open_start] + 60, times[open_start] + 180,
                               times[open_start] + 330, times[close_start - 1],
                               times[close_start] + 120, times[close_start] + 300, times[-1])):
        frame = frames[min(range(len(times)), key=lambda k: abs(times[k] - target))]
        review.paste(frame.resize((300, 250), Image.Resampling.NEAREST), ((i % 4) * 300, (i // 4) * 250))
    review.save(args.capture / 'door-animation-review.png')


if __name__ == '__main__':
    main()
