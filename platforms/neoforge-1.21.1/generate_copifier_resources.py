#!/usr/bin/env python3
"""Project the supplied three-state tool artwork through the shared runtime compactor."""
import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent/'build/generated/copifier-resources'
sys.path.insert(0, str(ROOT/'tools'))
from compact_runtime_assets import resized


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2)+'\n')


def main():
    assets = OUT/'assets/vandorlabs'
    for name in ('duplifier_off', 'duplifier_on', 'duplifier_multi'):
        source = next((ROOT/f'texture-packs/{pack}/assets/vandorlabs/textures/items/{name}.png'
                       for pack in ('additional', 'default')
                       if (ROOT/f'texture-packs/{pack}/assets/vandorlabs/textures/items/{name}.png').is_file()), None)
        if source is None:
            raise ValueError('Missing supplied tool artwork: '+name)
        target = assets/f'textures/item/{name}.png'
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        resized(target, 128)
        write(assets/f'models/item/{name}.json', {
            'parent': 'minecraft:item/generated', 'textures': {'layer0': f'vandorlabs:item/{name}'},
            'display': {'gui': {'scale': [.9, .9, .9]}}})
    write(assets/'models/item/duplifier.json', {
        'parent': 'vandorlabs:item/duplifier_off',
        'overrides': [{'predicate': {'vandorlabs:duplifier_mode': mode}, 'model': f'vandorlabs:item/{name}'}
                      for mode, name in ((.5, 'duplifier_on'), (1, 'duplifier_multi'))]})


if __name__ == '__main__':
    main()
