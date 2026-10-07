#!/usr/bin/env python3
"""Generate the solid-color split rocker in each supported mounting orientation."""
import itertools
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'src/main/resources/assets/vandorlabs/models/block/rocker_switch'
TEXTURES = {name: 'vandorlabs:blocks/thruster_controls/32px/' + name
            for name in ('metal', 'dark', 'grip', 'edge', 'amber', 'cyan')}
TEXTURES['particle'] = TEXTURES['metal']
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


def main():
    DEST.mkdir(parents=True, exist_ok=True)
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
            model = {'ambientocclusion': False, 'textures': TEXTURES, 'elements': elements}
            (DEST / f'{"on" if on else "off"}_{mount}.json').write_text(json.dumps(model, indent=2)+'\n')
    print('Generated 12 split-rocker Rocker Switch models')


if __name__ == '__main__':
    main()
