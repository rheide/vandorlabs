#!/usr/bin/env python3
"""Project canonical current shape model geometry; artwork remains in the existing housing atlas."""
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent / 'build/generated/shape-resources'
SOURCE = ROOT / 'src/main/resources/assets/vandorlabs'


def converted(value):
    if isinstance(value, list):
        return [converted(item) for item in value]
    if isinstance(value, dict):
        result = {key: converted(item) for key, item in value.items()}
        if 'model' in result and result['model'].startswith('vandorlabs:'):
            result['model'] = result['model'].replace('vandorlabs:', 'vandorlabs:block/', 1)
        return result
    if isinstance(value, str):
        if value == 'vandorlabs:blocks/dark_wall_panel':
            return 'vandorlabs:housing/dark_wall_panel'
        if value in ('block/half_slab', 'minecraft:block/half_slab'):
            return 'minecraft:block/slab'
        if value == 'block/block':
            return 'minecraft:block/block'
    return value


def copy(relative, target=None):
    value = converted(json.loads((SOURCE / relative).read_text()))
    path = OUT / 'assets/vandorlabs' / (target or relative)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    for name in ('programmable_slab', 'programmable_stairs'):
        copy('blockstates/' + name + '.json')
    for name in ('programmable_slab', 'programmable_stairs_straight', 'programmable_stairs_inner', 'programmable_stairs_outer'):
        copy('models/block/' + name + '.json')
    # The configured Tile model is the actual legacy untagged slab item source.
    copy('models/item/configured/programmable_slab_dark_wall_panel_tile.json', 'models/item/programmable_slab.json')
    copy('models/item/programmable_stairs.json')
    print('Native shape render resources: 2 blockstates, 6 canonical models; no copied artwork')


if __name__ == '__main__':
    main()
