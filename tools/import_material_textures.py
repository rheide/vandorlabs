#!/usr/bin/env python3
"""Append the three native 128px material finishes without shifting saved choices."""
import argparse
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
MATERIALS = [('01_satin_silver_128x128.png', 'satin_silver', 'Satin Silver'),
             ('02_cool_steel_128x128.png', 'cool_steel', 'Cool Steel'),
             ('03_pale_titanium_128x128.png', 'pale_titanium', 'Pale Titanium')]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path, help='Directory containing the three material PNGs')
    args = parser.parse_args()
    catalog = ROOT/'generated-resources/assets/vandorlabs/data/unified_textures.json'
    entries = json.loads(catalog.read_text())
    original_ids = [entry['id'] for entry in entries]
    known = {entry['id']: entry for entry in entries}
    # Preflight every asset before publishing any catalog entry.
    prepared = []
    for filename, identifier, label in MATERIALS:
        source = args.source/filename
        with Image.open(source) as image:
            assert image.size == (128, 128), (filename, image.size)
        entry = dict(id='material_'+identifier, label=label, category='Materials', source='materials/'+identifier)
        if entry['id'] in known:
            assert known[entry['id']] == entry, 'Existing material identity changed'
        prepared.append((source, entry))
    for source, entry in prepared:
        target = ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks'/(entry['source']+'.png')
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(source.read_bytes())
        if entry['id'] not in known:
            entries.append(entry)
    assert [entry['id'] for entry in entries[:len(original_ids)]] == original_ids
    catalog.write_text(json.dumps(entries, indent=2)+'\n')
    print('Imported three native material finishes; existing saved choice indices preserved')


if __name__ == '__main__':
    main()
