"""Native Rack uses the authored shared shell/module assets and padded item models."""
import json
import shutil


def generate(root, out):
    assets = out / 'assets/vandorlabs'
    def write(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, indent=2) + '\n')
    source = root / 'src/main/resources/assets/vandorlabs'
    for path in (source / 'models/block/module_rack').glob('*.json'):
        write(assets / 'models/block/module_rack' / path.name, json.loads(path.read_text()))
    for name in ('module_rack', 'blank_module', 'autopilot_module'):
        write(assets / f'models/item/{name}.json', json.loads((source / f'models/item/{name}.json').read_text()))
    write(assets / 'blockstates/module_rack.json', {'variants': {f'facing={face}': {'model': 'vandorlabs:block/module_rack/shell'} for face in ('down', 'up', 'north', 'south', 'west', 'east')}})
    for recipe_id in ('module_rack', 'blank_module', 'autopilot'):
        recipe = json.loads((source / f'recipes/{recipe_id}.json').read_text())
        recipe['result']['id'] = recipe['result'].pop('item')
        recipe['neoforge:conditions'] = [{'type': 'neoforge:item_exists', 'item': recipe['result']['id']}]
        write(out / f'data/vandorlabs/recipe/{recipe_id}.json', recipe)
    textures = root / 'texture-packs/additional/assets/vandorlabs/textures/blocks/module_rack'
    atlas = []
    for path in textures.glob('*.png'):
        target = assets / 'textures/blocks/module_rack' / path.name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, target)
        atlas.append({'type': 'minecraft:single', 'resource': f'vandorlabs:blocks/module_rack/{path.stem}'})
    return atlas
