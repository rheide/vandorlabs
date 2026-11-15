#!/usr/bin/env python3
"""Append the supplied Bathroom/Stall leaves and rename the Floor category without moving IDs."""
import argparse
import hashlib
import json
import shutil
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / 'generated-resources/assets/vandorlabs/data/unified_textures.json'
TARGET = ROOT / 'texture-packs/additional/assets/vandorlabs/textures/blocks/utility_doors'
PREFIX = 851
PREFIX_SHA = '3ab039cbccf5ad748c5ff0afe4fac7be6f32c6f203f415f7a2117ccf84c631b5'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path, nargs='?')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    if not args.check and args.source is None:
        parser.error('source directory required for import')
    original = json.loads(CATALOG.read_text())
    assert hashlib.sha256('\n'.join(e['id'] for e in original[:PREFIX]).encode()).hexdigest() == PREFIX_SHA, 'Saved choices moved'
    updated = [dict(e, category='Tiles') if e.get('category') == 'Floor' else dict(e) for e in original]
    for offset, label in enumerate(('Bathroom', 'Stall')):
        name = label.lower()
        descriptor = dict(id='utility_door_' + name, label=label, category='Doors/Utility',
                          source='utility_doors/' + name, rectangular=True, doorArtwork=True)
        index = next((i for i, e in enumerate(updated) if e['id'] == descriptor['id']), None)
        if index is None:
            assert len(updated) == PREFIX + offset, 'Append-only artwork choices'
            updated.append(descriptor)
        else:
            assert index == PREFIX + offset, 'Door choice moved'
            updated[index] = descriptor
        target = TARGET / (name + '.png')
        if args.source:
            source = args.source / (label + '.png')
            with Image.open(source) as image:
                assert image.size == (128, 256), (label, image.size)
            if args.check:
                assert source.read_bytes() == target.read_bytes(), 'Supplied artwork changed: ' + label
            else:
                TARGET.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, target)
        with Image.open(target) as image:
            assert image.size == (128, 256), label
    assert sum(e.get('category') == 'Tiles' for e in updated) == 13, 'Complete Tiles category'
    assert sum(e.get('category') == 'Doors/Utility' for e in updated) == 18, 'Complete Utility category'
    if args.check:
        assert original == updated, 'Catalog differs from imported policy'
    else:
        CATALOG.write_text(json.dumps(updated, indent=2) + '\n')
    print('More Utility artwork PASS: two exact128x256 leaves;13 Tiles;851 saved IDs/order retained')


if __name__ == '__main__':
    main()
