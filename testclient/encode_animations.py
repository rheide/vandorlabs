#!/usr/bin/env python3
"""Validate real motion captures and encode small, uniformly timed GIFs."""
import argparse
import csv
import json
import statistics
from pathlib import Path
from PIL import Image, ImageChops
ROOT = Path(__file__).resolve().parents[1]


def changed(a, b):
    red, green, blue = ImageChops.difference(a, b).split()
    mask = ImageChops.lighter(ImageChops.lighter(red, green), blue)
    return sum(mask.histogram()[25:]), mask.point(lambda v: 255 if v > 24 else 0).getbbox()


def encode(folder, destination):
    meta = json.loads((folder / 'capture.json').read_text())
    rows = list(csv.DictReader((folder / 'frames.tsv').open(), delimiter='\t'))
    assert len(rows) >= 25, f'{folder.name}: too few real frames'
    times = [float(row['elapsed_ms']) for row in rows]
    assert times[0] == 0 and all(b > a for a, b in zip(times, times[1:])), 'Invalid timestamps'
    assert rows[0]['open'] == rows[-1]['open'] == 'false', 'Unclosed cycle'
    on = next(i for i, row in enumerate(rows) if row['open'] == 'true')
    off = next(i for i in range(on + 1, len(rows)) if rows[i]['open'] == 'false')
    frames = []
    for row in rows:
        with Image.open(folder / row['file']) as image:
            assert image.size == (420, 350), 'Expected 70% size'
            frames.append(image.convert('RGB'))
    closed, opened = frames[0], frames[off - 1]
    movement, bounds = changed(closed, opened)
    assert movement > 256, f'{folder.name}: no visible deployed geometry'
    assert bounds[0] > 1 and bounds[1] > 1 and bounds[2] < 419 and bounds[3] < 349, f'{folder.name}: motion touches crop boundary {bounds}'
    threshold = max(30, movement * .05)
    for low, high in ((on, off), (off, len(rows))):
        assert sum(changed(frame, closed)[0] > threshold and changed(frame, opened)[0] > threshold for frame in frames[low:high]) >= 2, f'{folder.name}: missing intermediate motion'
    assert changed(closed, frames[-1])[0] < max(80, movement * .02), f'{folder.name}: geometry did not return'
    duration = meta['duration_ms']
    # A regular 20fps output uses nearest actual poses, without invented geometry.
    chosen = [min(range(len(times)), key=lambda i: abs(times[i] - t)) for t in range(0, duration, 50)]
    strip = Image.new('RGB', (120 * len(frames), 100))
    for i, frame in enumerate(frames):
        strip.paste(frame.resize((120, 100), Image.Resampling.BOX), (120 * i, 0))
    palette = strip.quantize(colors=256, method=Image.Quantize.MEDIANCUT)
    indexed = [frames[i].quantize(palette=palette, dither=Image.Dither.NONE) for i in chosen]
    output = destination / meta['output']
    output.parent.mkdir(parents=True, exist_ok=True)
    indexed[0].save(output, save_all=True, append_images=indexed[1:], duration=50, loop=0, disposal=1, optimize=True)
    with Image.open(output) as gif:
        assert gif.is_animated and gif.n_frames >= 8 and gif.info['loop'] == 0 and gif.size == (420, 350)
        total = 0
        for i in range(gif.n_frames):
            gif.seek(i)
            total += gif.info['duration']
        assert total == duration, 'Encoded timing drift'
        count = gif.n_frames
        # Decode the final GIF to review its palette and delta-frame disposal.
        review = Image.new('RGB', (840, 350))
        targets = [0, times[on] + 100, times[on] + 220, times[on] + (900 if meta['kind'] == 'ramp' else 350), times[off - 1], times[off] + 200, times[off] + (800 if meta['kind'] == 'ramp' else 350), duration - 50]
        for slot, target in enumerate(targets):
            stamp = 0
            for i in range(gif.n_frames):
                gif.seek(i)
                if stamp + gif.info['duration'] > target or i == gif.n_frames - 1:
                    review.paste(gif.convert('RGB').resize((210, 175)), ((slot % 4) * 210, (slot // 4) * 175))
                    break
                stamp += gif.info['duration']
        review.save(folder / 'review.png')
    gaps = [b - a for a, b in zip(times, times[1:])]
    result = dict(id=meta['id'], output=meta['output'], frames=count, captured=len(rows), duration_ms=duration, bytes=output.stat().st_size, median_gap_ms=round(statistics.median(gaps), 2), max_gap_ms=round(max(gaps), 2))
    print(f'PASS {meta["id"]}: {count} GIF frames, {duration}ms; gaps median {result["median_gap_ms"]}ms / max {result["max_gap_ms"]}ms')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('capture', type=Path, nargs='+')
    parser.add_argument('--report', type=Path)
    parser.add_argument('--destination', type=Path, default=ROOT / 'docs/images/gallery')
    args = parser.parse_args()
    selected = {}
    for capture in args.capture:
        folders = [capture] if (capture / 'capture.json').exists() else sorted(p.parent for p in capture.glob('*/capture.json'))
        for folder in folders:
            selected[json.loads((folder / 'capture.json').read_text())['id']] = folder
    folders = sorted(selected.values(), key=lambda folder: folder.name)
    assert folders, 'No completed captures'
    results = [encode(folder, args.destination) for folder in folders]
    (args.report or args.capture[-1] / 'encoding-results.json').write_text(json.dumps(results, indent=2) + '\n')
    print('Validated', len(results), 'motion GIFs')


if __name__ == '__main__':
    main()
