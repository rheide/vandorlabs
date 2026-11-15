"""Canonical Surface resources reuse Source artwork, recipes and padded item transforms."""
import json
import shutil


def generate(root, out):
    source = root / 'src/main/resources/assets/vandorlabs'
    assets = out / 'assets/vandorlabs'
    def write(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, indent=2) + '\n')
    model = json.loads((source / 'models/block/turntable_surface.json').read_text())
    # One body for each active normal. UV mapping is performed by shared per-cell terrain policy.
    for face in ('down', 'up', 'north', 'south', 'west', 'east'):
        body = json.loads(json.dumps(model))
        for direction in body['elements'][0]['faces']:
            body['textures'][direction] = 'vandorlabs:blocks/turntable/turntable_block_top_low' if direction == face else 'vandorlabs:blocks/turntable/turntable_block_bottom'
        write(assets / f'models/block/surface/{face}.json', body)
    variants = {f'facing={face},formed={formed}': {'model': f'vandorlabs:block/surface/{face}'} for face in ('down', 'up', 'north', 'south', 'west', 'east') for formed in ('false', 'true')}
    write(assets / 'blockstates/turntable_surface.json', {'variants': variants})
    write(assets / 'models/item/turntable_surface.json', {'parent': 'vandorlabs:block/surface/up'})
    recipe = json.loads((source / 'recipes/turntable_surface.json').read_text())
    recipe['result']['id'] = recipe['result'].pop('item')
    recipe['neoforge:conditions'] = [{'type': 'neoforge:item_exists', 'item': 'vandorlabs:turntable_surface'}]
    write(out / 'data/vandorlabs/recipe/turntable_surface.json', recipe)
    atlas = []
    for category in ('turntable', 'surface'):
        for path in (root / f'texture-packs/additional/assets/vandorlabs/textures/blocks/{category}').glob('*.png'):
            target = assets / f'textures/blocks/{category}' / path.name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(path, target)
            atlas.append({'type': 'minecraft:single', 'resource': f'vandorlabs:blocks/{category}/{path.stem}'})
    return atlas
