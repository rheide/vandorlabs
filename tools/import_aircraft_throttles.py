#!/usr/bin/env python3
"""Import the supplied 32px aircraft controls into the mounted signal-control family."""
import argparse
import copy
import itertools
import json
import math
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/vandorlabs'
KINDS = ('airliner_throttle', 'fighter_throttle')
MOUNTS = ('north', 'south', 'east', 'west', 'floor_z', 'floor_x', 'ceiling_z', 'ceiling_x')
STATES = ('off', 'low', 'medium', 'high')


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')


def corners(element):
    rotation = element.get('rotation')
    for point in itertools.product(*zip(element['from'], element['to'])):
        point = list(point)
        if rotation:
            assert not rotation.get('rescale'), 'Rescaled elements need separate bounds'
            axis = 'xyz'.index(rotation['axis'])
            a, b = (axis+1) % 3, (axis+2) % 3
            angle = math.radians(rotation['angle'])
            x, y = point[a]-rotation['origin'][a], point[b]-rotation['origin'][b]
            point[a] = rotation['origin'][a] + x*math.cos(angle) - y*math.sin(angle)
            point[b] = rotation['origin'][b] + x*math.sin(angle) + y*math.cos(angle)
        yield point


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('bundle', type=Path)
    source = parser.parse_args().bundle / 'pack-32px/assets/mctrek_flight_controls'
    texture_dir = ROOT / 'texture-packs/additional/assets/vandorlabs/textures/blocks/aircraft_throttles/32px'
    texture_dir.mkdir(parents=True, exist_ok=True)
    for texture in (source / 'textures/blocks').glob('*.png'):
        shutil.copyfile(texture, texture_dir / texture.name)
    bounds = []
    for kind in KINDS:
        for mount in MOUNTS:
            points = []
            for state in STATES:
                model = json.loads((source / 'models/block' / f'{kind}_{state}_{mount}.json').read_text())
                model['textures'] = {key: value.replace('mctrek_flight_controls:blocks/', 'vandorlabs:blocks/aircraft_throttles/32px/') for key, value in model['textures'].items()}
                write_json(ASSETS / 'models/block/aircraft_throttles/32px' / f'{kind}_{state}_{mount}.json', model)
                for element in model['elements']:
                    points.extend(corners(element))
            low = [min(point[i] for point in points)/16 for i in range(3)]
            high = [max(point[i] for point in points)/16 for i in range(3)]
            assert min(low) >= -1e-6 and max(high) <= 1.000001, (kind, mount, low, high)
            coordinates = ','.join(f'{number:.8f}' for number in low+high)
            bounds.append(f'        bounds.put("32/{kind}/{mount}",new AxisAlignedBB({coordinates}));')
        variants = copy.deepcopy(json.loads((ASSETS / 'blockstates/thruster_lever.json').read_text()))
        for variant in variants['variants'].values():
            variant['model'] = variant['model'].replace('thruster_controls/32px/thruster_lever', f'aircraft_throttles/32px/{kind}')
        write_json(ASSETS / 'blockstates' / f'{kind}.json', variants)
        item = json.loads((ASSETS / 'models/item/thruster_lever.json').read_text())
        item['parent'] = f'vandorlabs:block/aircraft_throttles/32px/{kind}_off_floor_z'
        item['display']['gui']['scale'] = [.56, .56, .56]
        write_json(ASSETS / 'models/item' / f'{kind}.json', item)
        write_json(ASSETS / 'recipes' / f'{kind}.json', {
            'type': 'minecraft:crafting_shaped',
            'pattern': ['ALA', 'LRL', ' G '] if kind == 'airliner_throttle' else [' AA', ' LR', ' G '],
            'key': {'A': {'item': 'vandorlabs:industrial_alloy_ingot'}, 'L': {'item': 'minecraft:lever'}, 'R': {'item': 'minecraft:redstone'}, 'G': {'item': 'minecraft:gold_ingot'}},
            'result': {'item': f'vandorlabs:{kind}', 'count': 1}})
    catalog = ROOT / 'generated-resources/assets/vandorlabs/data/blocks.json'
    entries = [entry for entry in json.loads(catalog.read_text()) if entry['id'] not in KINDS]
    entries.extend({'id': kind, 'type': 'switch', 'class': 'BlockSignalControl', 'item': True, 'control_kind': kind, 'detail': 32} for kind in KINDS)
    write_json(catalog, entries)
    lang = ROOT / 'generated-resources/assets/vandorlabs/lang/en_us.lang'
    lines = [line for line in lang.read_text().splitlines() if not any(line.startswith(f'tile.vandorlabs.{kind}.') for kind in KINDS)]
    lines.extend(f'tile.vandorlabs.{kind}.name={name}' for kind, name in zip(KINDS, ('Airliner Throttle', 'Fighter Throttle')))
    lang.write_text('\n'.join(lines)+'\n')
    java = ROOT / 'src/main/java/com/vandorlabs/blocks/SignalControlBounds.java'
    code = java.read_text()
    code = '\n'.join(line for line in code.splitlines() if not any(f'"32/{kind}/' in line for kind in KINDS))+'\n'
    code = code.replace('    }\n    static AxisAlignedBB', '\n'.join(bounds)+'\n    }\n    static AxisAlignedBB')
    java.write_text(code)
    print('Imported 64 aircraft models, two controls, textures, recipes, padded items and mounted bounds')


if __name__ == '__main__':
    main()
