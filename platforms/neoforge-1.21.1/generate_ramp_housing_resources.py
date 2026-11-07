#!/usr/bin/env python3
"""Project the authored controller housings; slab panels retain their square-atlas half UVs."""
import copy
import itertools
import json
from pathlib import Path
import shutil
import sys

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent/'build/generated/ramp-housing-resources'
sys.path.insert(0, str(ROOT/'tools'))
from compact_runtime_assets import resized

MODES = ('ramp', 'filled_ramp', 'lift', 'extend', 'escalator_up', 'escalator_down', 'moat')
FACES = ('down', 'up', 'north', 'south', 'west', 'east')
HORIZONTAL = FACES[2:]


def source_model(kind, name):
    relative = f'assets/vandorlabs/models/{kind}/{name}.json'
    for directory in ('generated-resources', 'src/main/resources'):
        candidate = ROOT/directory/relative
        if candidate.is_file():
            return json.loads(candidate.read_text())
    raise ValueError('Missing authored model: '+relative)


def write(relative, value):
    target = OUT/relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, indent=2)+'\n')


def modern_path(path):
    return path.replace(':blocks/', ':block/').replace(':items/', ':item/')


def model(kind, name, textures):
    value = source_model(kind, name)
    if 'parent' in value:
        value['parent'] = modern_path(value['parent'])
    for key, texture in value.get('textures', {}).items():
        texture = modern_path(texture)
        value['textures'][key] = texture
        if texture.startswith('vandorlabs:'):
            textures.add(texture.split(':', 1)[1])
    for override in value.get('overrides', []):
        override['model'] = modern_path(override['model'])
        # Native ItemProperties predicates are clamped to [0,1].
        override['predicate']['vandorlabs:ramp_mode'] /= 6
    write(f'assets/vandorlabs/models/{kind}/{name}.json', value)


def states(slab):
    name = 'programmable_ramp_slab' if slab else 'programmable_ramp'
    source = json.loads((ROOT/f'generated-resources/assets/vandorlabs/blockstates/{name}.json').read_text())['variants']
    result = {}
    halves = ('bottom', 'top') if slab else (None,)
    for active, scan, facing, mode, half in itertools.product(('false', 'true'), FACES, HORIZONTAL, range(7), halves):
        # Source scanFacing uses horizontal travel-facing for a horizontal active_face.
        effective = scan if scan in ('down', 'up') else facing
        original = {'active': active, 'active_face': effective, 'mode': str(mode)}
        if half is not None:
            original['half'] = half
        key = ','.join(f'{key}={original[key]}' for key in sorted(original))
        entry = copy.deepcopy(source[key])
        entry['model'] = entry['model'].replace('vandorlabs:', 'vandorlabs:block/', 1)
        native = dict(original, active_face=scan, facing=facing)
        result[','.join(f'{key}={native[key]}' for key in sorted(native))] = entry
    write(f'assets/vandorlabs/blockstates/{name}.json', {'variants': result})


def main():
    textures = set()
    for mode in MODES:
        for active in ('off', 'on'):
            model('block', f'ramp_mode_{mode}_{active}', textures)
            for half, face in itertools.product(('bottom', 'top'), ('horizontal', 'up', 'down')):
                model('block', f'ramp_slab_{mode}_{active}_{half}_{face}', textures)
        if mode != 'ramp':
            model('item', 'ramp_mode_'+mode, textures)
        model('item', 'ramp_slab_'+mode, textures)
    for name in ('programmable_ramp', 'programmable_ramp_slab'):
        model('item', name, textures)
        recipe = json.loads((ROOT/f'src/main/resources/assets/vandorlabs/recipes/{name}.json').read_text())
        recipe['result']['id'] = recipe['result'].pop('item')
        required = ('programmable_ramp', name) if name.endswith('_slab') else (name,)
        recipe['neoforge:conditions'] = [{'type': 'neoforge:item_exists', 'item': 'vandorlabs:'+item}
                                        for item in dict.fromkeys(required)]
        write(f'data/vandorlabs/recipe/{name}.json', recipe)
    states(False)
    states(True)
    for texture in sorted(textures):
        source = next((ROOT/f'texture-packs/{pack}/assets/vandorlabs/textures/{texture}.png'
                       for pack in ('additional', 'default')
                       if (ROOT/f'texture-packs/{pack}/assets/vandorlabs/textures/{texture}.png').is_file()), None)
        if source is None:
            raise ValueError('Missing supplied Ramp artwork: '+texture)
        target = OUT/f'assets/vandorlabs/textures/{texture}.png'
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        resized(target, 128)
        metadata = source.with_suffix('.png.mcmeta')
        if metadata.is_file():
            shutil.copyfile(metadata, target.with_suffix('.png.mcmeta'))
    print(f'Ramp housing resources: 1008 listed states, 14 item modes, {len(textures)} authored sprites')


if __name__ == '__main__':
    main()
