#!/usr/bin/env python3
"""Check actual 28-case housing item gallery padding against the legacy raster."""
import argparse
from collections import Counter
import json
from pathlib import Path

from PIL import Image


SCALE = 2
COLUMNS = 7
ROWS = 4
CELL = 22 * SCALE
SLOT = 16 * SCALE
INSET = 3 * SCALE


def gallery_cells(path):
    image = Image.open(path).convert('RGB')
    width, height = image.size
    assert (width, height) == (1280, 720), 'Expected actual GUI-scale-2 capture'
    left = ((width // SCALE - COLUMNS * 22) // 2) * SCALE
    top = ((height // SCALE - ROWS * 22) // 2) * SCALE
    return [image.crop((left + i % COLUMNS * CELL, top + i // COLUMNS * CELL,
                        left + (i % COLUMNS + 1) * CELL, top + (i // COLUMNS + 1) * CELL))
            for i in range(COLUMNS * ROWS)]


def padding(cell):
    # The first legacy cell retains GUI-lighting color on its background. Infer each
    # cell's own dominant color; comparing RGB to one global background is invalid.
    background = Counter(cell.get_flattened_data()).most_common(1)[0][0]
    points = [(x, y) for y in range(CELL) for x in range(CELL)
              if max(abs(a - b) for a, b in zip(cell.getpixel((x, y)), background)) > 3]
    assert len(points) >= 100, 'Blank or missing actual item preview'
    x0 = min(x for x, _ in points)
    y0 = min(y for _, y in points)
    x1 = max(x for x, _ in points)
    y1 = max(y for _, y in points)
    margins = [x0 - INSET, y0 - INSET,
               INSET + SLOT - 1 - x1, INSET + SLOT - 1 - y1]
    assert min(margins) >= 1, f'Item touches or exceeds inventory slot: {margins}'
    return margins


def check(reference, actual):
    expected = padding(reference)
    observed = padding(actual)
    assert observed == expected, f'Actual item raster size differs: {observed} != {expected}'
    return observed


def require_rejection(reference, candidate, label):
    try:
        check(reference, candidate)
    except AssertionError:
        return
    raise AssertionError(f'{label} negative control accepted')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--legacy', type=Path, required=True)
    parser.add_argument('--modern', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    legacy = gallery_cells(args.legacy)
    modern = gallery_cells(args.modern)
    failures = []
    margins = []
    for index, (reference, actual) in enumerate(zip(legacy, modern)):
        try:
            margins.append({'case': index, 'padding': check(reference, actual)})
        except AssertionError as error:
            failures.append({'case': index, 'error': str(error)})
    reference = legacy[2]
    background = Counter(reference.get_flattened_data()).most_common(1)[0][0]
    blank = Image.new('RGB', (CELL, CELL), background)
    require_rejection(reference, blank, 'blank')
    clipped = reference.copy()
    # A visible pixel at the slot boundary must fail regardless of model metadata.
    clipped.putpixel((INSET, INSET), (255, 0, 255))
    require_rejection(reference, clipped, 'clipped')
    smaller = blank.copy()
    smaller.paste(reference.resize((CELL - 4, CELL - 4), Image.Resampling.NEAREST), (2, 2))
    require_rejection(reference, smaller, 'undersized')
    result = {'status': 'FAIL' if failures else 'PASS', 'cases': len(legacy),
              'padding': margins, 'failures': failures,
              'negative_controls': ['blank', 'clipped', 'undersized']}
    args.report.write_text(json.dumps(result, indent=2) + '\n')
    assert not failures, f'{len(failures)}/28 item raster padding contracts differ; see report'
    print('PASS 28 actual item previews: legacy raster bounds, visible padding on all sides; three negatives rejected')


if __name__ == '__main__':
    main()
