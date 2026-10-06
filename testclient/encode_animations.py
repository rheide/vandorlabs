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
    if meta['kind'] == 'signal':
        return encode_signal(folder, destination, meta, rows)
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


def encode_signal(folder, destination, meta, rows):
    assert len(rows) >= 60, f'{folder.name}: too few real frames'
    times = [float(row['elapsed_ms']) for row in rows]
    assert times[0] == 0 and all(b > a for a, b in zip(times, times[1:])), 'Invalid timestamps'
    levels = [int(row['control_level']) for row in rows]
    transitions = [level for i, level in enumerate(levels) if i == 0 or level != levels[i-1]]
    assert transitions == [0, 5, 10, 15, 0], f'Unexpected signal cycle: {transitions}'
    frames = []
    for row in rows:
        with Image.open(folder / row['file']) as image:
            assert image.size == (420, 350)
            frames.append(image.convert('RGB'))
    stable = {}
    for level in (0, 5, 10, 15):
        matching = [i for i, row in enumerate(rows) if int(row['control_level']) == int(row['consumer_level']) == level]
        assert len(matching) >= 8, f'{folder.name}: consumer did not settle at {level}'
        if level == 0:
            first_on = next(i for i, value in enumerate(levels) if value == 5)
            matching = [i for i in matching if i < first_on]
        index = matching[len(matching)//2]
        stable[level] = index
        expected_particles = meta['consumer'] == 'thruster' and level >= 10
        assert (rows[index]['particles'] == 'true') == expected_particles, 'Wrong particle threshold'
    # Both the left-hand control and right-hand consumer must visibly change.
    for box in ((35, 60, 210, 300), (210, 60, 385, 300)):
        assert changed(frames[stable[0]].crop(box), frames[stable[15]].crop(box))[0] > 50, 'Control/consumer change not visible'
    duration = meta['duration_ms']
    chosen = [min(range(len(times)), key=lambda i: abs(times[i] - t)) for t in range(0, duration, 100)]
    strip = Image.new('RGB', (120 * len(frames), 100))
    for i, frame in enumerate(frames):
        strip.paste(frame.resize((120, 100), Image.Resampling.BOX), (120 * i, 0))
    palette = strip.quantize(colors=256, method=Image.Quantize.MEDIANCUT)
    indexed = [frames[i].quantize(palette=palette, dither=Image.Dither.NONE) for i in chosen]
    output = destination / meta['output']
    output.parent.mkdir(parents=True, exist_ok=True)
    indexed[0].save(output, save_all=True, append_images=indexed[1:], duration=100, loop=0, disposal=1, optimize=True)
    with Image.open(output) as gif:
        assert gif.is_animated and gif.info['loop'] == 0 and gif.size == (420, 350)
        total = 0
        for i in range(gif.n_frames):
            gif.seek(i)
            total += gif.info['duration']
        assert total == duration, 'Encoded timing drift'
        count = gif.n_frames
    review = Image.new('RGB', (420*4, 350))
    for slot, level in enumerate((0, 5, 10, 15)):
        target = times[stable[level]]
        with Image.open(output) as gif:
            elapsed = 0
            for frame in range(gif.n_frames):
                gif.seek(frame)
                if elapsed + gif.info['duration'] > target:
                    review.paste(gif.convert('RGB'), (420*slot, 0))
                    break
                elapsed += gif.info['duration']
    review.save(folder / 'decoded-review.png')
    print(f'PASS: {meta["id"]}, levels 0/5/10/15/0, {count} GIF frames, {duration}ms, {output.stat().st_size} bytes')
    gaps = [b-a for a, b in zip(times, times[1:])]
    return {'id': meta['id'], 'output': str(output.relative_to(destination)), 'frames': count, 'captured': len(rows), 'duration_ms': duration, 'bytes': output.stat().st_size, 'median_gap_ms': round(statistics.median(gaps), 2), 'max_gap_ms': round(max(gaps), 2), 'output_step_ms': 100}


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
    print('Validated', len(results), 'documentation GIFs')


if __name__ == '__main__':
    main()
