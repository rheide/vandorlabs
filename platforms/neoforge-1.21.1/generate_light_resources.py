#!/usr/bin/env python3
"""Mirror authored Light inventory templates and recipes; native registration is separate."""
import json
from pathlib import Path
import re
import shutil

PLATFORM = Path(__file__).resolve().parent
ROOT = PLATFORM.parents[1]
OUT = PLATFORM / 'build/generated/light-resources'
NAMES = ('programmable_light', 'programmable_light_frame', 'programmable_light_slab')


def write(relative, value):
    target = OUT / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, separators=(',', ':')) + '\n')


def main():
    source = (ROOT / 'src/portable/java/com/vandorlabs/tiles/ProgrammableLightTextures.java').read_text()
    array = re.search(r'String\[\]\s+IDS\s*=\s*\{(.*?)\}', source, re.S)
    if array is None:
        raise ValueError('Missing source Light artwork IDs')
    styles = re.findall(r'"([^"\\]+)"', array.group(1))
    if not styles:
        raise ValueError('Empty source Light artwork IDs')
    entries = json.loads((PLATFORM / 'build/generated/cube-resources/assets/vandorlabs/data/housing_finishes.json').read_text())
    catalog = {entry['id']: entry['texture'] for entry in entries}
    textures = {'vandorlabs:blocks/dark_wall_panel': catalog['dark_wall_panel']}
    for style in styles:
        for on in (False, True):
            textures['vandorlabs:blocks/' + style + ('_on' if on else '_off')] = catalog['light_' + style + ('' if on else '_off')]
    if OUT.exists():
        shutil.rmtree(OUT)
    write('assets/vandorlabs/models/block/native_light_empty.json', {
        'ambientocclusion': False, 'textures': {'particle': catalog['light_' + styles[0]]}, 'elements': [],
    })
    for name in NAMES:
        base = json.loads((ROOT / 'src/main/resources/assets/vandorlabs/models/item' / (name + '.json')).read_text())
        write('assets/vandorlabs/models/item/' + name + '.json', base)
        write('assets/vandorlabs/blockstates/' + name + '.json', {'variants': {'': {'model': 'vandorlabs:block/native_light_empty'}}})
        for style in styles:
            for on in (False, True):
                variant = name + '_' + style + ('_on' if on else '_off')
                model = json.loads((ROOT / 'src/main/resources/assets/vandorlabs/models/item/configured' / (variant + '.json')).read_text())
                for key, value in model.get('textures', {}).items():
                    if not value.startswith('#'):
                        model['textures'][key] = textures[value]
                write('assets/vandorlabs/models/item/configured/' + variant + '.json', model)
        recipe = json.loads((ROOT / 'src/main/resources/assets/vandorlabs/recipes' / (name + '.json')).read_text())
        recipe['result']['id'] = recipe['result'].pop('item')
        ingredients = recipe.get('ingredients', list(recipe.get('key', {}).values()))
        dependencies = {name}
        dependencies.update(ingredient['item'].split(':', 1)[1] for ingredient in ingredients if ingredient['item'].startswith('vandorlabs:'))
        recipe['neoforge:conditions'] = [{'type': 'neoforge:item_exists', 'item': 'vandorlabs:' + dependency} for dependency in sorted(dependencies)]
        write('data/vandorlabs/recipe/' + name + '.json', recipe)
    print('Native Light resources: ' + str(len(NAMES) * len(styles) * 2) + ' authored configured item templates, three empty housings and conditional source recipes; registration separate')


if __name__ == '__main__':
    main()
