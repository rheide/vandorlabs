#!/usr/bin/env python3
"""Import the four pre-split overhead-bin sets, without changing source files."""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageFilter

SETS = {
    'blue_gray_overhead_bin': ('Blue-Gray Overhead Bin', {
        'front': 'Blue-gray overhead bin front texture-2.png',
        'side': 'Blue-gray overhead bin side panel-3.png',
        'top': 'Blue-gray overhead bin panel-1.png'}),
    'metal_overhead_bin': ('Metal Overhead Bin', {
        'front': 'Metal overhead Bin front-5.png',
        'side': 'Metal overhead bin texture-6.png',
        'top': 'Metal overhead bin top texture-4.png'}),
    'square_matte_overhead_bin': ('Square Matte Overhead Bin', {
        'front': 'Square Matte overhead-bin front-3.png',
        'side': 'Square matte overhead bin side texture-1.png',
        'top': 'Square Matte Overhead Bin Top-2.png'}),
    'square_charcoal_overhead_bin': ('Square Charcoal Overhead Bin', {
        'front': 'Square charcoal overhead-bin door-6.png',
        'side': 'Square charcoal overhead-bin side texture-5.png',
        'top': 'Square charcoal overhead bin texture-4.png'}),
}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    target = root / 'texture-packs/additional/assets/vandorlabs/textures/blocks/storage'
    manifest = root / 'texture-packs/additional/catalog.json'
    catalog = json.loads(manifest.read_text())
    provenance = []
    updates = {}
    for name, (label, faces) in SETS.items():
        for face, filename in faces.items():
            source = args.source / filename
            with Image.open(source) as original:
                if original.width != original.height:
                    raise ValueError(f'{filename}: expected a square face')
                size = original.size
                panel = original.convert('RGB').resize((70, 70), Image.Resampling.BOX)
                panel = panel.filter(ImageFilter.UnsharpMask(radius=0.65, percent=85, threshold=3))
                panel = panel.resize((140, 140), Image.Resampling.NEAREST)
                output = target / (name + '_' + face + '.png')
                panel.save(output, optimize=True)
            provenance.append(dict(source=filename, size=size,
                sha256=hashlib.sha256(source.read_bytes()).hexdigest(),
                output=str(output.relative_to(root))))
        entry = dict(id='storage_' + name, label=label, category='Storage',
                     source='storage/' + name + '_front', top='storage/' + name + '_top',
                     side='storage/' + name + '_side')
        updates[entry['id']] = entry
    # Replace matching entries in place; new choices only append.
    catalog = [updates.pop(e['id'], e) for e in catalog] + list(updates.values())
    manifest.write_text(json.dumps(catalog, indent=2) + '\n')
    (root / 'texture-packs/additional/storage-bins-sources.json').write_text(
        json.dumps(provenance, indent=2) + '\n')
    print('Imported four storage sets, twelve 140x140 faces')

if __name__ == '__main__':
    main()
