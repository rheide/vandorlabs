#!/usr/bin/env python3
"""Mirror authored standalone Screen icons; configured image selection remains Source-defined."""
import json
from pathlib import Path
import shutil

PLATFORM = Path(__file__).resolve().parent
ROOT = PLATFORM.parents[1]
OUT = PLATFORM / 'build/generated/screen-item-resources'
NAMES = ('programmable_viewscreen', 'programmable_console', 'programmable_half_console',
         'programmable_half_input', 'programmable_input', 'programmable_diagonal_screen',
         'programmable_diagonal_half_screen')


def write(name, value):
    target = OUT / name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, separators=(',', ':')) + '\n')


def main():
    catalog = json.loads((PLATFORM / 'build/generated/cube-resources/assets/vandorlabs/data/housing_finishes.json').read_text())
    entries = {entry['id']: entry for entry in catalog}
    if OUT.exists():
        shutil.rmtree(OUT)
    materials = {}
    for name in NAMES:
        source = json.loads((ROOT / 'src/main/resources/assets/vandorlabs/models/item' / (name + '.json')).read_text())
        parent = source.get('parent', '')
        if ':' not in parent:
            source['parent'] = 'minecraft:' + parent
        for key, texture in source['textures'].items():
            if texture.startswith('#'):
                continue
            if texture == 'vandorlabs:blocks/programmable_glass/door_side':
                mapped = dict(texture='vandorlabs:housing/native_screen_door_side', u=1, v=1)
            elif texture.startswith('vandorlabs:blocks/unified/'):
                entry = entries[texture.removeprefix('vandorlabs:blocks/unified/')]
                mapped = dict(texture=entry['texture'], u=entry['u'], v=entry['v'])
            else:
                raise ValueError('Unaudited authored Screen icon texture: ' + texture)
            materials[texture] = mapped
            source['textures'][key] = mapped['texture']
        write('assets/vandorlabs/models/item/' + name + '.json', source)
        write('assets/vandorlabs/blockstates/' + name + '.json', {'variants': {'': {'model': 'vandorlabs:block/native_screen_empty'}}})
        recipe = json.loads((ROOT / 'src/main/resources/assets/vandorlabs/recipes' / (name + '.json')).read_text())
        recipe['result']['id'] = recipe['result'].pop('item')
        ingredients = recipe.get('ingredients', list(recipe.get('key', {}).values()))
        required = {name}
        required.update(item['item'].split(':', 1)[1] for item in ingredients if item['item'].startswith('vandorlabs:'))
        recipe['neoforge:conditions'] = [{'type': 'neoforge:item_exists', 'item': 'vandorlabs:' + item} for item in sorted(required)]
        write('data/vandorlabs/recipe/' + name + '.json', recipe)
    write('assets/vandorlabs/models/block/native_screen_empty.json', {'ambientocclusion': False, 'textures': {'particle': entries['screen_bridge_bare_engineering_static']['texture']}, 'elements': []})
    write('assets/vandorlabs/data/screen_item_sprites.json', materials)
    target = OUT / 'assets/vandorlabs/textures/housing/native_screen_door_side.png'
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / 'texture-packs/default/assets/vandorlabs/textures/blocks/programmable_glass/door_side.png', target)
    print('Native Screen item resources: seven authored templates/conditional recipes, source-sized housing sprite; registration separate')


if __name__ == '__main__':
    main()
