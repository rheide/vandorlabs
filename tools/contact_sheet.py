#!/usr/bin/env python3
"""Tile screenshots into one labelled, downscaled image for quick review.

Usage: tools/contact_sheet.py OUT.png IMAGE... [--width 480] [--columns 3]
Reviewing one sheet costs far less than opening each full-size screenshot. Crop a single
original at full resolution only after the sheet shows where to look.
"""
import argparse
import os
import sys

from PIL import Image, ImageDraw


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('out')
    parser.add_argument('images', nargs='+')
    parser.add_argument('--width', type=int, default=480)
    parser.add_argument('--columns', type=int, default=3)
    args = parser.parse_args()
    images = [(path, Image.open(path).convert('RGB')) for path in sorted(args.images)]
    if not images:
        sys.exit('contact_sheet: no images')
    width = args.width
    height = max(1, round(width * images[0][1].height / images[0][1].width))
    columns = min(args.columns, len(images))
    rows = (len(images) + columns - 1) // columns
    sheet = Image.new('RGB', (columns * width, rows * height), 'black')
    draw = ImageDraw.Draw(sheet)
    for index, (path, image) in enumerate(images):
        x, y = (index % columns) * width, (index // columns) * height
        sheet.paste(image.resize((width, height)), (x, y))
        label = os.path.splitext(os.path.basename(path))[0]
        draw.rectangle((x, y, x + 7 * len(label) + 6, y + 14), fill='black')
        draw.text((x + 3, y + 2), label, fill='white')
    sheet.save(args.out)
    print(args.out)


if __name__ == '__main__':
    main()
