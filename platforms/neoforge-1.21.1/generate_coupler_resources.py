"""Project authored Square and Slab Coupler models, geometry, artwork and recipes."""
import json
import shutil

IDS = ('square_coupler', 'vehicle_coupler_slab')


def generate(root, output):
    source = root / 'src/main/resources/assets/vandorlabs'
    target = output / 'assets/vandorlabs'

    def read(path):
        return json.loads((source / path).read_text())

    def write(path, data):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, indent=2) + '\n')

    model_ids = ('square_coupler_released', 'square_coupler_locked',
                 'vehicle_coupler_slab_released_bottom', 'vehicle_coupler_slab_locked_bottom',
                 'vehicle_coupler_slab_released_top', 'vehicle_coupler_slab_locked_top')
    geometry = {}
    textures = set()
    for name in model_ids:
        path = f'models/block/{name}.json'
        model = read(path)
        model['parent'] = 'minecraft:block/block'
        textures.update(model['textures'].values())
        geometry[name] = [element['from'] + element['to'] for element in model['elements']]
        write(target / path, model)
    write(target / 'data/coupler_geometry.json', geometry)

    atlas = []
    for reference in sorted(textures):
        path = reference.removeprefix('vandorlabs:') + '.png'
        sprite = target / 'textures' / path
        sprite.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(root / 'texture-packs/additional/assets/vandorlabs/textures' / path, sprite)
        atlas.append(dict(type='minecraft:single', resource=reference))

    for name in IDS:
        states = read(f'blockstates/{name}.json')
        if name == 'vehicle_coupler_slab':
            states['variants'] = {key: variant for key, variant in states['variants'].items()
                                  if not key.startswith(('facing=up,', 'facing=down,'))}
        for variant in states['variants'].values():
            variant['model'] = variant['model'].replace('vandorlabs:', 'vandorlabs:block/')
        write(target / f'blockstates/{name}.json', states)
        write(target / f'models/item/{name}.json', read(f'models/item/{name}.json'))
        write(output / f'data/vandorlabs/loot_table/blocks/{name}.json', {
            'type': 'minecraft:block', 'pools': [{'rolls': 1,
            'entries': [{'type': 'minecraft:item', 'name': 'vandorlabs:' + name}],
            'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    for name in (*IDS, 'vehicle_coupler_slab_recombine'):
        recipe = read(f'recipes/{name}.json')
        recipe['result']['id'] = recipe['result'].pop('item')
        write(output / f'data/vandorlabs/recipe/{name}.json', recipe)
    return atlas
