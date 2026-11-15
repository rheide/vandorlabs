"""Translate the canonical industrial table models without duplicating artwork."""
import json
import shutil


def generate(root, resources):
    source = root / 'src/main/resources/assets/vandorlabs'
    assets = resources / 'assets/vandorlabs'

    def write(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, indent=2) + '\n')

    states = json.loads((source / 'blockstates/industrial_table.json').read_text())
    for variant in states['variants'].values():
        variant['model'] = variant['model'].replace('vandorlabs:', 'vandorlabs:block/')
    write(assets / 'blockstates/industrial_table.json', states)
    for suffix in ('standalone', 'left_end', 'right_end', 'middle'):
        model = json.loads((source / f'models/block/industrial_table_{suffix}.json').read_text())
        if model.get('parent', '').startswith('block/'):
            model['parent'] = 'minecraft:' + model['parent']
        model['textures'] = {key: value.replace(':blocks/', ':block/') for key, value in model['textures'].items()}
        write(assets / f'models/block/industrial_table_{suffix}.json', model)
    item = json.loads((source / 'models/item/industrial_table.json').read_text())
    item['display'] = {'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.56, 0.56, 0.56]}}
    write(assets / 'models/item/industrial_table.json', item)
    candidates = [root / f'texture-packs/{pack}/assets/vandorlabs/textures/blocks/industrial_table.png' for pack in ('default', 'additional')] + [source / 'textures/blocks/industrial_table.png']
    texture = next(path for path in candidates if path.exists())
    target = assets / 'textures/block/industrial_table.png'
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(texture, target)
    recipe = json.loads((source / 'recipes/industrial_table.json').read_text())
    recipe['result']['id'] = recipe['result'].pop('item')
    recipe['neoforge:conditions'] = [{'type': 'neoforge:item_exists', 'item': 'vandorlabs:industrial_table'}]
    write(resources / 'data/vandorlabs/recipe/industrial_table.json', recipe)
    write(resources / 'data/vandorlabs/loot_table/blocks/industrial_table.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'vandorlabs:industrial_table'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    return [{'type': 'minecraft:single', 'resource': 'vandorlabs:block/industrial_table'}]
