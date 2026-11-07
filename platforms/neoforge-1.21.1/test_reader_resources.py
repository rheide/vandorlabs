#!/usr/bin/env python3
"""Exact authored frame records and source RGBA, independent from actual native baking."""
import hashlib
import json
from PIL import Image
from generate_reader_resources import ROOT, ASSETS


def main():
    manifest = json.loads((ASSETS / 'data/reader_artwork.json').read_text())
    originals = {entry['id']: entry['original'] for entry in manifest['textures']}
    quads = 0
    for name in manifest['models'] + manifest.get('items', []):
        relative = name.split(':', 1)[1] + '.json'
        source = json.loads((ROOT / 'src/main/resources/assets/vandorlabs/models' / relative).read_text())
        target = json.loads((ASSETS / 'models' / relative).read_text())
        target['textures'] = {key: 'minecraft:blocks/concrete_black' if value == 'minecraft:block/black_concrete' else originals[value] for key, value in target['textures'].items()}
        if source != target:
            raise AssertionError('Reader authored geometry/materials changed ' + name)
        count = sum(len(element['faces']) for element in source['elements'])
        if name in manifest['models'] and manifest['faces'][name] != count:
            raise AssertionError('Reader frame face coverage ' + name)
        if name in manifest['models']:
            quads += count
    for entry in manifest['textures']:
        relative = entry['original'].split(':', 1)[1] + '.png'
        source = next(ROOT / directory / 'assets/vandorlabs/textures' / relative for directory in ('texture-packs/additional', 'texture-packs/default', 'src/main/resources') if (ROOT / directory / 'assets/vandorlabs/textures' / relative).is_file())
        a = Image.open(source).convert('RGBA')
        b = Image.open(ASSETS / 'textures' / (entry['id'].split(':', 1)[1] + '.png')).convert('RGBA')
        if a.size != b.size or a.tobytes() != b.tobytes() or hashlib.sha256(b.tobytes()).hexdigest() != entry['rgbaSha256']:
            raise AssertionError('Reader standard pixels changed ' + entry['id'])
    if len(manifest['models']) != 24 or len(manifest.get('items', [])) != 2 or len(manifest['textures']) != 8 or quads != 576:
        raise AssertionError('Incomplete reader inventory ' + str((len(manifest['models']), len(manifest['textures']), quads)))
    print('Reader resources source PASS models=24 items=2 sprites=8 frameQuads=576 exact authored geometry/materials/source RGBA and item transforms; actual native baking checked separately')


if __name__ == '__main__':
    main()
