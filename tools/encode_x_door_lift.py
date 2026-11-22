#!/usr/bin/env python3
"""Encode the X-door lift capture (XDoorLiftAnimation) into the 2.12 version-page GIF.

The crop is the union of all moving pixels plus a margin, at 4:3. Frames keep their real
100 ms capture cadence; the closed and raised endpoints are held a little longer. One
shared palette keeps colours stable between frames.
"""
import argparse
import csv
from pathlib import Path

from PIL import Image, ImageChops


def motion_box(frames, threshold=24):
    first = frames[0].convert('RGB')
    box = None
    for frame in frames[1:]:
        diff = ImageChops.difference(first, frame.convert('RGB')).convert('L').point(lambda v: 255 if v > threshold else 0)
        found = diff.getbbox()
        if found:
            box = found if box is None else (min(box[0], found[0]), min(box[1], found[1]), max(box[2], found[2]), max(box[3], found[3]))
    if box is None:
        raise SystemExit('No motion found in the capture')
    return box


def fit(box, size, margin, aspect=4 / 3):
    left, top, right, bottom = box
    width, height = right - left + 2 * margin, bottom - top + 2 * margin
    if width / height < aspect:
        width = height * aspect
    else:
        height = width / aspect
    cx, cy = (left + right) / 2, (top + bottom) / 2
    width, height = min(width, size[0]), min(height, size[1])
    x0 = int(round(min(max(cx - width / 2, 0), size[0] - width)))
    y0 = int(round(min(max(cy - height / 2, 0), size[1] - height)))
    return x0, y0, x0 + int(width), y0 + int(height)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('capture', type=Path, help='Directory with frame-*.png and frames.tsv')
    parser.add_argument('output', type=Path)
    parser.add_argument('--width', type=int, default=480)
    parser.add_argument('--margin', type=int, default=40)
    parser.add_argument('--colors', type=int, default=128)
    args = parser.parse_args()
    rows = list(csv.DictReader((args.capture / 'frames.tsv').open(), delimiter='\t'))
    frames = [Image.open(args.capture / row['file']).convert('RGB') for row in rows]
    crop = fit(motion_box(frames), frames[0].size, args.margin)
    height = round(args.width * (crop[3] - crop[1]) / (crop[2] - crop[0]))
    scaled = [frame.crop(crop).resize((args.width, height), Image.LANCZOS) for frame in frames]
    # One palette from a sample of every phase keeps the door, arms and sky stable.
    picks = scaled[::max(1, len(scaled) // 16)]
    sample = Image.new('RGB', (args.width, height * len(picks)))
    for index, frame in enumerate(picks):
        sample.paste(frame, (0, index * height))
    palette = sample.quantize(colors=args.colors, method=Image.MEDIANCUT)
    durations = []
    for index, row in enumerate(rows):
        duration = 100
        if index == 0:
            duration = 800
        elif index == len(rows) - 1:
            duration = 600
        elif row['phase'] == 'raised' and (index + 1 == len(rows) or rows[index + 1]['phase'] != 'raised'):
            duration = 400
        durations.append(duration)
    quantized = [frame.quantize(palette=palette, dither=Image.NONE) for frame in scaled]
    # Merge frames that do not visibly change (endpoint holds) into one longer frame.
    merged, merged_durations = [quantized[0]], [durations[0]]
    for frame, duration in zip(quantized[1:], durations[1:]):
        if ImageChops.difference(merged[-1].convert('RGB'), frame.convert('RGB')).getbbox() is None:
            merged_durations[-1] += duration
        else:
            merged.append(frame)
            merged_durations.append(duration)
    quantized, durations = merged, merged_durations
    args.output.parent.mkdir(parents=True, exist_ok=True)
    quantized[0].save(args.output, save_all=True, append_images=quantized[1:], duration=durations, loop=0, optimize=True, disposal=1)
    print(f'{args.output}: {args.width}x{height} frames={len(quantized)} duration_ms={sum(durations)} crop={crop} bytes={args.output.stat().st_size}')


if __name__ == '__main__':
    main()
