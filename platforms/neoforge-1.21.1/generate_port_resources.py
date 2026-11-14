"""Native six-sided port models reuse the normalized Forge artwork."""
import json
import shutil


def generate(root, out):
    assets = out / 'assets/vandorlabs'
    source = root / 'texture-packs/additional/assets/vandorlabs/textures/blocks/resource_ports'
    target = assets / 'textures/blocks/resource_ports'
    target.mkdir(parents=True, exist_ok=True)
    names = ['port_hull'] + [f'{kind}_{face}_{active}' for kind in ('power', 'fluid', 'matter') for face in ('in', 'out') for active in ('off', 'on')]
    for name in names:
        shutil.copyfile(source / f'{name}.png', target / f'{name}.png')
    def write(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, indent=2) + '\n')
    for kind in ('power', 'fluid', 'matter'):
        textures = {face: 'vandorlabs:blocks/resource_ports/port_hull' for face in ('down', 'up', 'north', 'south', 'west', 'east', 'particle')}
        textures['north'] = f'vandorlabs:blocks/resource_ports/{kind}_in_off'
        textures['south'] = f'vandorlabs:blocks/resource_ports/{kind}_out_off'
        write(assets / f'models/block/{kind}_port.json', dict(parent='minecraft:block/cube', textures=textures))
        write(assets / f'blockstates/{kind}_port.json', dict(variants={f'facing={face}': dict(model=f'vandorlabs:block/{kind}_port') for face in ('down', 'up', 'north', 'south', 'west', 'east')}))
        recipe = json.loads((root / f'src/main/resources/assets/vandorlabs/recipes/{kind}_port.json').read_text())
        recipe['result']['id'] = recipe['result'].pop('item')
        recipe['neoforge:conditions'] = [{'type': 'neoforge:item_exists', 'item': f'vandorlabs:{kind}_port'}]
        write(out / f'data/vandorlabs/recipe/{kind}_port.json', recipe)
        write(assets / f'models/item/{kind}_port.json', dict(parent=f'vandorlabs:block/{kind}_port', display=dict(gui=dict(rotation=[30, 225, 0], translation=[0, 0, 0], scale=[.55, .55, .55]))))
    return [dict(type='minecraft:single', resource=f'vandorlabs:blocks/resource_ports/{name}') for name in names]
