#!/usr/bin/env python3
"""Generate Programmable Corner Glass item models from the default glass item models.

The flat corner reuses the default Programmable Glass pane and frame, bent 45 degrees at its
middle. The sloped corner reuses the default Programmable Diagonal Glass model, turned 45
degrees in the inventory view like the placed block. Both keep their parents' GUI transform.
"""
import copy
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
ITEMS = ROOT / 'src/main/resources/assets/vandorlabs/models/item'
BEND = 8.0


def load(name):
    return json.loads((ITEMS / 'configured' / f'{name}.json').read_text())


def split(element, low, high):
    """Clip an element to low <= x <= high, scaling its north/south UVs to match."""
    x0, x1 = element['from'][0], element['to'][0]
    a, b = max(x0, low), min(x1, high)
    if b - a <= 1e-9:
        return None
    part = copy.deepcopy(element)
    part['from'][0], part['to'][0] = a, b
    width = x1 - x0
    for face, data in part['faces'].items():
        if face not in ('north', 'south') or 'uv' not in data:
            continue
        u0, v0, u1, v1 = data['uv']
        # North faces run from +x to -x in texture space.
        f0, f1 = ((x1 - b) / width, (x1 - a) / width) if face == 'north' else ((a - x0) / width, (b - x0) / width)
        data['uv'] = [u0 + (u1 - u0) * f0, v0, u0 + (u1 - u0) * f1, v1]
    if a > x0:
        part['faces'].pop('west', None)
    if b < x1:
        part['faces'].pop('east', None)
    return part


def flat_corner():
    model = load('glass_regular_1_clear_t1_f1')
    elements = []
    for element in model['elements']:
        left = split(element, 0, BEND)
        right = split(element, BEND, 16)
        if left:
            elements.append(left)
        if right:
            right['rotation'] = {'origin': [BEND, 8, 8], 'axis': 'y', 'angle': -45, 'rescale': False}
            elements.append(right)
    model['elements'] = elements
    return model


def sloped_corner():
    model = load('glass_diagonal_0_clear_t1_f1')
    gui = model['display']['gui']
    gui['rotation'] = [gui['rotation'][0], gui['rotation'][1] - 45, gui['rotation'][2]]
    return model


def main(check):
    stale = []
    for name, model in (('programmable_corner_glass', flat_corner()),
                        ('programmable_diagonal_corner_glass', sloped_corner())):
        path = ITEMS / f'{name}.json'
        text = json.dumps(model, indent=2) + '\n'
        if check:
            if not path.exists() or path.read_text() != text:
                stale.append(str(path.relative_to(ROOT)))
        else:
            path.write_text(text)
    if stale:
        sys.exit('Stale corner glass item models: ' + ', '.join(stale))


if __name__ == '__main__':
    main('--check' in sys.argv[1:])
