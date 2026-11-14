#!/usr/bin/env python3
"""Verify Module Rack asset fit, source reproducibility and inventory padding."""
import itertools
import json
import math
from pathlib import Path
import subprocess
import sys

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/vandorlabs'
MODELS = ASSETS / 'models/block/module_rack'
TEXTURES = ROOT / 'texture-packs/additional/assets/vandorlabs/textures/blocks/module_rack'
TEXTURE_NAMES = ('housing', 'module', 'trim', 'dark', 'indicators', 'contacts')


def require(value, reason):
    if not value:
        raise AssertionError(reason)


def read(path):
    return json.loads(path.read_text())


def named(model, name):
    return [element for element in model['elements'] if element.get('name') == name]


def gui_bounds(model):
    transform = model['display']['gui']
    pitch, yaw, roll = [math.radians(value) for value in transform.get('rotation', [0, 0, 0])]
    require(roll == 0, 'inventory bounds check needs explicit roll support')
    scale = transform['scale']
    points = []
    for element in model['elements']:
        for point in itertools.product(*zip(element['from'], element['to'])):
            x, y, z = [(point[index] - 8) * scale[index] for index in range(3)]
            x, z = x * math.cos(yaw) + z * math.sin(yaw), -x * math.sin(yaw) + z * math.cos(yaw)
            y, z = y * math.cos(pitch) - z * math.sin(pitch), y * math.sin(pitch) + z * math.cos(pitch)
            points.append((x, y))
    return (min(p[0] for p in points), max(p[0] for p in points),
            min(p[1] for p in points), max(p[1] for p in points))


def main():
    subprocess.run([sys.executable, str(ROOT / 'tools/gen_module_rack.py'), '--check'], check=True)
    shell = read(MODELS / 'shell.json')
    for name, axis, low, high in [('left_rim_1px', 0, 0, 1),
                                  ('top_rim_1px', 1, 15, 16),
                                  ('bottom_rim_1px', 1, 0, 1)]:
        elements = named(shell, name)
        require(len(elements) == 1, 'missing exact rim ' + name)
        require(elements[0]['from'][axis] == low and elements[0]['to'][axis] == high,
                'rim must be one Minecraft model pixel ' + name)
    fascia = named(shell, 'fascia_3px_recessed_1px')
    require(len(fascia) == 1, 'missing recessed fascia')
    require(fascia[0]['from'][0] == 13 and fascia[0]['to'][0] == 16 and fascia[0]['to'][2] == 15,
            'fascia must be three pixels wide and one pixel behind the casing face')
    require(not any('card' in element.get('name', '') or 'reader' in element.get('name', '')
                    for element in shell['elements']), 'reader remains on requested plain slot-control panel')
    variants = read(ASSETS / 'blockstates/module_rack.json')['variants']
    require(set(variants) == {'facing=' + name for name in ('north', 'south', 'east', 'west', 'up', 'down')},
            'all six rack mounting directions must have a model')
    quads = 0
    for path in MODELS.glob('*.json'):
        model = read(path)
        for element in model.get('elements', []):
            low, high = element['from'], element['to']
            require(all(0 <= low[axis] <= high[axis] <= 16 for axis in range(3))
                    and sum(low[axis] < high[axis] for axis in range(3)) >= 2,
                    'model extends beyond owning block: ' + str(path) + ' ' + element.get('name', ''))
            for face in element.get('faces', {}).values():
                uv = face.get('uv')
                require(uv is None or all(0 <= value <= 16 for value in uv), 'UV outside texture atlas')
            quads += len(element.get('faces', {}))
    for slot in range(4):
        model = read(MODELS / ('slot_%d.json' % slot))
        bodies = named(model, 'cassette_body')
        require(len(bodies) == 1, 'one closed cassette body per slot')
        body = bodies[0]
        low, high = body['from'], body['to']
        require(low[0] >= 1 and high[0] <= 13 and high[1] - low[1] == 3, 'cassette slot fit')
        require(abs(low[1] - (11.75 - 3.5 * slot)) < 1e-8, 'module row height/index')
        handles = named(model, 'vertical_handle')
        require(len(handles) == 2, 'exactly two vertical side handles')
        require(handles[0]['to'][0] < 7 and handles[1]['from'][0] > 7,
                'handles must be at opposite ends of the front')
        for handle in handles:
            a, b = handle['from'], handle['to']
            require(b[1] - a[1] > b[0] - a[0], 'handle must be vertical')
            require(low[1] <= a[1] < b[1] <= high[1], 'handle overlaps neighboring module')
            require(a[2] > high[2] and b[2] <= 16, 'handle needs real finger gap within casing envelope')
        off = read(MODELS / ('indicator_%d_off.json' % slot))['elements']
        on = read(MODELS / ('indicator_%d_on.json' % slot))['elements']
        require(len(off) == len(on) == 1, 'one slot button per row and state')
        require(off[0]['from'] == on[0]['from'] and off[0]['to'] == on[0]['to'], 'indicator state shifts geometry')
        a, b = on[0]['from'], on[0]['to']
        require(13 <= a[0] < b[0] <= 16 and b[2] < 16, 'button must stay on recessed panel')
        require(abs((a[1] + b[1]) / 2 - (low[1] + high[1]) / 2) < 1e-8, 'button not aligned with its slot')
    for name in TEXTURE_NAMES:
        path = TEXTURES / (name + '.png')
        require(path.exists(), 'texture absent from packaged texture source: ' + name)
        with Image.open(path) as image:
            require(image.width == image.height and image.width <= 64, 'oversized/non-square texture ' + name)
            require(image.width & (image.width - 1) == 0, 'non-power-of-two atlas')
    map_models = (ASSETS / 'dynmap-models.txt').read_text().splitlines()
    map_textures = (ASSETS / 'dynmap-texture.txt').read_text().splitlines()
    require(map_models.count('customblock:id=%module_rack,state=*,'
                             'class=com.vandorlabs.dynmap.ModuleRackRenderer') == 1,
            'rack must use its native geometry Dynmap renderer exactly once')
    for name in TEXTURE_NAMES:
        line = ('texture:id=module_rack_' + name + ',filename=assets/vandorlabs/textures/blocks/'
                'module_rack/' + name + '.png,xcount=1,ycount=1')
        require(map_textures.count(line) == 1, 'rack Dynmap texture registration ' + name)
    mapping = 'block:id=%module_rack,state=*,' + ','.join(
        'patch%d=0:module_rack_%s' % (index, name) for index, name in enumerate(TEXTURE_NAMES))
    require(map_textures.count(mapping + ',transparency=SEMITRANSPARENT') == 1,
            'rack Dynmap patch texture order must match its six native material indices')
    for path in (ASSETS / 'models/item/module_rack.json', MODELS / 'blank_module.json'):
        bounds = gui_bounds(read(path))
        require(all(-7.5 < bound < 7.5 for bound in bounds), 'inventory icon lacks visible padding: ' + str(bounds))
        print(path.name + ' projected GUI bounds=' + repr(tuple(round(x, 3) for x in bounds)))
    print('Module Rack assets PASS: 1px rim, 3px recessed fascia, four bays/buttons, vertical handles, '
          'six facings, texture dimensions, Dynmap mapping, source reproducibility; component face records=' + str(quads))


if __name__ == '__main__':
    main()
