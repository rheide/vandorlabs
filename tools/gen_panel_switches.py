#!/usr/bin/env python3
"""Generate the solid-color split rocker in each supported mounting orientation."""
import itertools
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/vandorlabs'
TEXTURES = {name: 'vandorlabs:blocks/thruster_controls/32px/' + name
            for name in ('metal', 'dark', 'grip', 'edge', 'amber', 'cyan')}
TEXTURES['particle'] = TEXTURES['metal']
TEXTURES['lit_cyan'] = 'vandorlabs:blocks/industrial_power_lever'
MOUNTS = {
    'floor': lambda x, y, z: (x, y, z),
    'ceiling': lambda x, y, z: (x, 16-y, 16-z),
    'north': lambda x, y, z: (x, z, 16-y),
    'south': lambda x, y, z: (16-x, z, y),
    'east': lambda x, y, z: (y, z, x),
    'west': lambda x, y, z: (16-y, z, 16-x),
}


def geometry(on):
    boxes = [
        ([5, 0, 4], [11, .5, 12], 'metal'),
        ([5, .5, 4], [5.5, 1.75, 12], 'dark'),
        ([10.5, .5, 4], [11, 1.75, 12], 'dark'),
        ([5.5, .5, 4], [10.5, 1.75, 4.5], 'dark'),
        ([5.5, .5, 11.5], [10.5, 1.75, 12], 'dark'),
        ([5.5, .5, 4.5], [10.5, .75, 11.5], 'grip'),
        ([5.5, .75, 7.875], [10.5, 1.5, 8.125], 'dark'),
    ]
    for top in (False, True):
        low, high = (8.25, 11.375) if top else (4.625, 7.75)
        pressed = top == on
        height = 1.25 if pressed else 1.625
        color = ('cyan' if on else 'amber') if pressed else 'edge'
        # Four non-overlapping rails outline each half; the active half is recessed.
        boxes += [
            ([5.625, .75, low], [5.875, height, high], color),
            ([10.125, .75, low], [10.375, height, high], color),
            ([5.875, .75, low], [10.125, height, low+.25], color),
            ([5.875, .75, high-.25], [10.125, height, high], color),
            ([5.875, .75, low+.25], [10.125, height-.125, high-.25], 'cyan' if on and top else 'metal'),
        ]
    return boxes


def button_geometry(on):
    height=1.125 if on else 1.625
    return [
        ([5,0,5],[11,.5,11],'metal'),
        ([5,.5,5],[5.5,1.5,11],'dark'),
        ([10.5,.5,5],[11,1.5,11],'dark'),
        ([5.5,.5,5],[10.5,1.5,5.5],'dark'),
        ([5.5,.5,10.5],[10.5,1.5,11],'dark'),
        ([5.5,.5,5.5],[10.5,.75,10.5],'grip'),
        ([5.75,.75,5.75],[10.25,height,10.25],'cyan' if on else 'edge'),
    ]


def generate(name,geometry):
    dest=ASSETS / 'models/block' / name
    dest.mkdir(parents=True, exist_ok=True)
    for on in (False, True):
        for mount, transform in MOUNTS.items():
            elements = []
            for low, high, color in geometry(on):
                points = [transform(8+(x-8)*.92, y*.92, 8+(z-8)*.92)
                          for x,y,z in itertools.product(*zip(low, high))]
                elements.append({
                    'from': [min(point[axis] for point in points) for axis in range(3)],
                    'to': [max(point[axis] for point in points) for axis in range(3)],
                    'faces': {face: {'texture': '#' + color, 'uv': [0, 0, 16, 16]}
                              for face in ('north', 'south', 'east', 'west', 'up', 'down')},
                })
            # Reuse just the lever atlas's cyan light tile on outward button faces.
            # Dark sidewalls and the solid palette keep the controls simple.
            if on:
                for element in elements:
                    if element['faces']['up']['texture']=='#cyan':
                        face={'floor':'up','ceiling':'down'}.get(mount,mount)
                        element['faces'][face]={'texture':'#lit_cyan','uv':[12,0,16,4]}
            model = {'ambientocclusion': False, 'textures': TEXTURES, 'elements': elements}
            (dest / f'{"on" if on else "off"}_{mount}.json').write_text(json.dumps(model, indent=2)+'\n')
    print('Generated 12 mounted models for '+name)


def main():
    generate('rocker_switch',geometry)
    generate('push_button',button_geometry)
    for rel in ('blockstates/rocker_switch.json','models/item/rocker_switch.json'):
        source=ASSETS/rel
        target=ASSETS/rel.replace('rocker_switch','push_button')
        target.write_text(source.read_text().replace('rocker_switch','push_button'))


if __name__ == '__main__':
    main()
