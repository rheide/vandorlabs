#!/usr/bin/env python3
"""Split user-supplied top/side/front strips without redrawing their artwork."""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageFilter

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('source', type=Path)
parser.add_argument('export', type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
target = root / 'texture-packs/additional/assets/vandorlabs/textures/blocks/storage'
target.mkdir(parents=True, exist_ok=True)
args.export.mkdir(parents=True, exist_ok=True)
entries = []
for source in sorted(args.source.glob('*.png')):
    sheet = Image.open(source).convert('RGB')
    width, height = sheet.size
    if width != 3 * height:
        raise SystemExit(f'{source}: expected three square panels, got {sheet.size}')
    name = source.stem.replace('-', '_')
    for index, face in enumerate(('top', 'side', 'front')):
        panel = sheet.crop((height * index, 0, height * (index + 1), height))
        # Area reduction preserves fine lines; a restrained sharpen restores edges.
        # A 70px working grid enlarged exactly 2x keeps pixels crisp at 140px.
        panel = panel.resize((70, 70), Image.Resampling.BOX)
        panel = panel.filter(ImageFilter.UnsharpMask(radius=0.65, percent=85, threshold=3))
        panel = panel.resize((140, 140), Image.Resampling.NEAREST)
        filename = name + '_' + face + '.png'
        panel.save(target / filename, optimize=True)
        (args.export / filename).write_bytes((target / filename).read_bytes())
    entries.append(dict(id='storage_' + name, label=source.stem.replace('-', ' ').title(),
                        category='Storage', source='storage/' + name + '_front',
                        top='storage/' + name + '_top', side='storage/' + name + '_side'))
manifest = root / 'texture-packs/additional/catalog.json'
previous = json.loads(manifest.read_text())
# Replace matching IDs in place; append new ones to preserve saved indices.
updates = {entry['id']: entry for entry in entries}
result = [updates.pop(entry['id'], entry) for entry in previous]
result.extend(updates.values())
manifest.write_text(json.dumps(result, indent=2) + '\n')
(args.export / 'README.txt').write_text('Storage textures: top, side, front from left to right in each original sheet.\n'
    'Each PNG is 140x140 pixels; reduced on a 70px grid, lightly sharpened, enlarged 2x with nearest-neighbor.\n'
    'Original artwork and panel boundaries are preserved. Cabinet is the default storage set.\n')
print(f'Imported {len(entries)} Storage sets; exported {len(entries)*3} PNGs to {args.export}')
