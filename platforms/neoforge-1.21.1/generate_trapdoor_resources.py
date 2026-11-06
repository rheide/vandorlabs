#!/usr/bin/env python3
"""Retain authored trapdoor inventory geometry and recipes for native registration."""
import json
from pathlib import Path
import shutil

PLATFORM = Path(__file__).resolve().parent
ROOT = PLATFORM.parents[1]
OUT = PLATFORM / 'build/generated/trapdoor-resources'
NAMES = ('programmable_trapdoor', 'programmable_diagonal_trapdoor')
DEFAULT = 'imported_trapdoors_cyan_lit_armored_sci_fi_hatch_4'


def write(relative, value):
    target = OUT / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, separators=(',', ':')) + '\n')


def main():
    catalog = json.loads((PLATFORM / 'build/generated/cube-resources/assets/vandorlabs/data/housing_finishes.json').read_text())
    texture = next(entry['texture'] for entry in catalog if entry['id'] == DEFAULT)
    if OUT.exists():
        shutil.rmtree(OUT)
    write('assets/vandorlabs/models/block/native_trapdoor_empty.json', {
        'ambientocclusion': False, 'textures': {'particle': texture}, 'elements': [],
    })
    for name in NAMES:
        model = json.loads((ROOT / 'src/main/resources/assets/vandorlabs/models/item' / (name + '.json')).read_text())
        for key, value in model['textures'].items():
            if not value.startswith('#'):
                model['textures'][key] = texture
        write('assets/vandorlabs/models/item/' + name + '.json', model)
        write('assets/vandorlabs/blockstates/' + name + '.json', {
            'variants': {'': {'model': 'vandorlabs:block/native_trapdoor_empty'}},
        })
        recipe = json.loads((ROOT / 'src/main/resources/assets/vandorlabs/recipes' / (name + '.json')).read_text())
        recipe['result']['id'] = recipe['result'].pop('item')
        inputs = recipe.get('ingredients', list(recipe.get('key', {}).values()))
        ids = sorted({name, *(ingredient['item'].split(':', 1)[1] for ingredient in inputs if ingredient['item'].startswith('vandorlabs:'))})
        recipe['neoforge:conditions'] = [{'type': 'neoforge:item_exists', 'item': 'vandorlabs:' + identifier} for identifier in ids]
        write('data/vandorlabs/recipe/' + name + '.json', recipe)
    print('Native Trapdoors: two authored inventory models, empty fluid housings and conditional source recipes; registration separate')


if __name__ == '__main__':
    main()
