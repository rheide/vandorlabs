#!/usr/bin/env python3
"""Reject the measured coplanar metal bands across real diagonal screen artwork."""
import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image


def measure(image):
    image = image.convert('RGB')
    if image.size != (1280, 720):
        raise AssertionError('diagonal art requires the actual 1280x720 world capture')
    pixels = np.asarray(image, dtype=int)
    # Both real camera poses retain the isolated diagonal face inside this world region.
    region = pixels[240:480, 400:880]
    red, green, blue = region[:, :, 0], region[:, :, 1], region[:, :, 2]
    cyan = (red < 60) & (green > red + 20) & (blue > red + 20) & (np.maximum(green, blue) > 70)
    ys, xs = np.where(cyan)
    if len(xs) < 100:
        raise AssertionError('authored cyan screen pixels are missing')
    x0, x1, y0, y1 = int(xs.min()) + 400, int(xs.max()) + 401, int(ys.min()) + 240, int(ys.max()) + 241
    if x1 - x0 < 70 or y1 - y0 < 60:
        raise AssertionError('diagonal artwork is too small or edge-on for the band regression')
    inset = (x1 - x0) // 5
    inside = pixels[y0 + 5:y1 - 5, x0 + inset:x1 - inset]
    # Housing metal is gray; the authored face has a dark background and cyan/orange instruments.
    # The broken screenshot replaces broad scanlines of that artwork with coplanar housing metal.
    metal = (inside.min(axis=2) > 22) & ((inside.max(axis=2) - inside.min(axis=2)) <= 25) & (inside.max(axis=2) < 150)
    bands = int((metal.mean(axis=1) > .65).sum())
    return {'art_bounds': [x0, y0, x1, y1], 'wide_metal_rows': bands, 'sampled_rows': int(inside.shape[0]), 'metal_fraction': float(metal.mean())}


def inspect(image):
    result = measure(image)
    if result['wide_metal_rows'] > 8:
        raise AssertionError(f'coplanar housing obscures artwork: {result["wide_metal_rows"]} wide metal scanlines')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target', type=Path, required=True)
    parser.add_argument('--off-target', type=Path, required=True)
    parser.add_argument('--negative', type=Path)
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    report = {'status': 'PASS', 'target': inspect(Image.open(args.target)), 'off_target': inspect(Image.open(args.off_target))}
    if args.negative:
        negative = measure(Image.open(args.negative))
        if negative['wide_metal_rows'] < 20:
            raise AssertionError('retained broken screenshot does not contain the established metal-band defect')
        try:
            inspect(Image.open(args.negative))
        except AssertionError:
            report['negative_rejected'] = negative
        else:
            raise AssertionError('retained broken screenshot was accepted')
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / 'diagonal-art-depth-contracts.json').write_text(json.dumps(report, indent=2) + '\n')
    print('Diagonal art pixels PASS: readable target/off-target authored face without coplanar housing bands; retained broken image rejected when supplied')


if __name__ == '__main__':
    main()
