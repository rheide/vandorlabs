#!/usr/bin/env python3
"""Current plain-wall render assets; geometry is emitted from shared kernels at model build time."""
import json
from pathlib import Path
import shutil
from generate_shape_resources import converted

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/main/resources/assets/vandorlabs'
OUT = Path(__file__).resolve().parent / 'build/generated/panel-resources'


def metal(value):
    if isinstance(value, dict):
        return {key: metal(item) for key, item in value.items()}
    if isinstance(value, list):
        return [metal(item) for item in value]
    return 'vandorlabs:frame-default/metal_side' if value == 'vandorlabs:blocks/programmable_glass/metal_side' else value


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    paths = ['blockstates/programmable_wall.json', 'models/block/programmable_wall.json', 'models/item/programmable_wall.json']
    for relative in paths:
        value = metal(converted(json.loads((SOURCE / relative).read_text())))
        if relative.startswith('models/item/'):
            # This unused legacy slot must not request a missing standalone glass sprite.
            value['textures'].pop('glass', None)
        target = OUT / 'assets/vandorlabs' / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(value, indent=2) + '\n')
    print('Native plain panel assets: 24 canonical variants, current item geometry; existing housing/metal atlas reused')


if __name__ == '__main__':
    main()
