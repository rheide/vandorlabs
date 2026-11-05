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
    recipe=json.loads((SOURCE/'recipes/programmable_wall.json').read_text())
    recipe['result']['id']=recipe['result'].pop('item')
    target=OUT/'data/vandorlabs/recipe/programmable_wall.json';target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(recipe)+'\n')
    loot={'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'vandorlabs:programmable_wall','functions':[{'function':'minecraft:copy_components','source':'block_entity','include':['minecraft:block_entity_data']}]}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]}
    target=OUT/'data/vandorlabs/loot_table/blocks/programmable_wall.json';target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(loot)+'\n')
    print('Native plain panel assets: 24 canonical variants, current item geometry; existing housing/metal atlas reused')


if __name__ == '__main__':
    main()
