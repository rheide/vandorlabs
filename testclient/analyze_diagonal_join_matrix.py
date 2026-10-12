#!/usr/bin/env python3
"""Check forward/reverse placement screenshots and produce a labeled visual index."""
from pathlib import Path
import sys
from PIL import Image, ImageDraw, ImageChops

root = Path(sys.argv[1])
pairs = sorted(root.glob('shot_gallery_join_matrix_*_forward.png'))
assert len(pairs) == 72, f'Expected 72 join arrangements; found {len(pairs)}'
thumbs = []
for path in pairs:
    reverse = path.with_name(path.name.replace('_forward', '_reverse'))
    assert reverse.exists(), reverse.name
    a = Image.open(path).convert('RGB').crop((120,200,1160,510))
    b = Image.open(reverse).convert('RGB').crop((120,200,1160,510))
    channels=ImageChops.difference(a,b).split()
    difference=ImageChops.lighter(ImageChops.lighter(channels[0],channels[1]),channels[2])
    histogram=difference.histogram()
    fraction=sum(histogram[25:])/(a.width*a.height)
    assert fraction < .015, f'Placement-order render difference {fraction:.2%}: {path.name}'
    tile = Image.new('RGB', (320, 202), '#17232d')
    tile.paste(Image.open(path).convert('RGB').resize((320, 180)), (0, 22))
    label = path.stem.replace('shot_gallery_join_matrix_', '').replace('_forward', '')
    ImageDraw.Draw(tile).text((5, 5), label, fill='white')
    thumbs.append(tile)
for material in range(2):
    sheet = Image.new('RGB', (320 * 6, 202 * 6), '#17232d')
    for i, tile in enumerate(thumbs[material * 36:(material + 1) * 36]):
        sheet.paste(tile, ((i % 6) * 320, (i // 6) * 202))
    sheet.save(root / ('join_matrix_' + ('glass' if material == 0 else 'wall') + '.png'))
print('PASS: all 72 join arrangements retain their rendered appearance in both placement orders; labeled contact sheets saved')
