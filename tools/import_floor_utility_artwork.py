#!/usr/bin/env python3
"""Append floor and Utility Door artwork, retaining every earlier saved choice."""
import argparse
import hashlib
import json
import re
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / 'generated-resources/assets/vandorlabs/data/unified_textures.json'
TARGET = ROOT / 'texture-packs/additional/assets/vandorlabs/textures/blocks'
PREFIX_COUNT = 833
PREFIX_SHA = '7293a15d941bfd2227c05ec08f8cb1083b5547b351e7842c62dbb0abfe523768'
FLOORS = ('Floor Tile Blue', 'Floor Tile Charcoal', 'Floor Tile Green', 'Floor Tile Warm', 'Floor Tile White', 'Limestone Tile', 'Staggered Tiles Blue', 'Staggered Tiles Green', 'Staggered Tiles Grey', 'Staggered Tiles Red', 'Staggered Tiles Warm', 'Terrazo', 'Terrazo Tile')
DOORS = ('Bathroom White', 'Modern Oak Windowed', 'Windowed Utility', 'Windowed White')


def descriptors():
    for category, names in [('Floor', FLOORS), ('Doors/Utility', DOORS)]:
        door = category.startswith('Doors/')
        for label in names:
            name = re.sub(r'[^a-z0-9]+', '_', label.lower()).strip('_')
            entry = dict(id=('utility_door_' if door else 'floor_') + name, label=label,
                         category=category, source=('utility_doors/' if door else 'floor/') + name)
            if door:
                entry.update(rectangular=True, doorArtwork=True)
            size = (128, 256) if door else (32, 32) if label.startswith('Staggered') else (64, 64)
            yield entry, size


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path, nargs='?')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    if not args.check and args.source is None:
        parser.error('source artwork directory is required for import')
    entries = json.loads(CATALOG.read_text())
    assert hashlib.sha256('\n'.join(e['id'] for e in entries[:PREFIX_COUNT]).encode()).hexdigest() == PREFIX_SHA, 'Saved texture choices moved'
    expected = list(entries)
    for offset, (entry, size) in enumerate(descriptors()):
        index = next((i for i, value in enumerate(expected) if value["id"] == entry["id"]), None)
        assert index is None or index == PREFIX_COUNT + offset, "Imported saved choice moved"
        if index is None:
            assert len(expected) == PREFIX_COUNT + offset, "Append after the established saved choices"
            expected.append(entry)
        else:
            expected[index] = entry
        target = TARGET / (entry['source'] + '.png')
        if args.source:
            source = args.source / entry['category'] / (entry['label'] + '.png')
            with Image.open(source) as art:
                # Keep the authored alpha; LANCZOS integrates enlarged source pixels
                # into the established catalog sizes. Native pixel sheets stay exact.
                art = art.convert('RGBA')
                if art.size != size:
                    art = art.resize(size, Image.Resampling.LANCZOS)
                if args.check:
                    with Image.open(target) as actual:
                        assert actual.size == size and actual.convert('RGBA').tobytes() == art.tobytes(), entry['id']
                else:
                    target.parent.mkdir(parents=True, exist_ok=True)
                    art.save(target, optimize=True)
        with Image.open(target) as actual:
            assert actual.size == size, (entry['id'], actual.size)
    if args.check:
        assert entries == expected, 'Imported catalog differs from append-only artwork definitions'
    else:
        CATALOG.write_text(json.dumps(expected, indent=2) + '\n')
    print('Floor/Utility artwork PASS: 13 floors (eight64x64, five32x32), four128x256 leaves; original833 saved IDs/order retained')


if __name__ == '__main__':
    main()
