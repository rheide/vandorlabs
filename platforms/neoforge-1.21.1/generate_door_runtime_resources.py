#!/usr/bin/env python3
"""Prepare empty Door chunk housings and the unchanged recipes for native activation."""
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent / 'build/generated/door-runtime-resources'


def write(relative, value):
    target = OUT / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, separators=(',', ':')) + '\n')


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    # Animated bodies are separate; this model carries the immutable contained-fluid chunk snapshot.
    write('assets/vandorlabs/models/block/native_door_empty.json', {
        'ambientocclusion': False,
        'textures': {'particle': 'vandorlabs:door-artwork/blocks/space_doors/medium/standard'},
        'elements': [],
    })
    for name in ('programmable_door', 'space_rotating_door', 'space_sliding_door', 'large_programmable_door'):
        write('assets/vandorlabs/blockstates/' + name + '.json', {
            'variants': {'': {'model': 'vandorlabs:block/native_door_empty'}},
        })
    for name in ('programmable_door', 'large_programmable_door'):
        recipe = json.loads((ROOT / 'src/main/resources/assets/vandorlabs/recipes' / (name + '.json')).read_text())
        recipe['result']['id'] = recipe['result'].pop('item')
        recipe['neoforge:conditions'] = [{'type': 'neoforge:item_exists', 'item': 'vandorlabs:' + name}]
        if name == 'large_programmable_door':
            recipe['neoforge:conditions'].append({'type': 'neoforge:item_exists', 'item': 'vandorlabs:programmable_door'})
        write('data/vandorlabs/recipe/' + name + '.json', recipe)
    print('Native Door runtime resources: four empty chunk housings, two unchanged conditional recipes; registration separate')


if __name__ == '__main__':
    main()
