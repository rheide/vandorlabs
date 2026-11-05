#!/usr/bin/env python3
"""Current panel render assets; the independent palette helper has separate build inputs."""
import json
from pathlib import Path
import shutil
from generate_shape_resources import converted
from glass_palette_resources import generate_glass_palette

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
    for name in ['programmable_wall','programmable_porthole_wall','programmable_diagonal_wall']:
        for relative in ['blockstates/'+name+'.json','models/block/'+name+'.json','models/item/'+name+'.json']:
            value = metal(converted(json.loads((SOURCE / relative).read_text())))
            if name=='programmable_diagonal_wall' and relative.startswith('blockstates/'):
                # Legacy diagonal metadata always reads depth0; the other source variants are identical.
                variants={}
                for key,variant in value['variants'].items():
                    key=','.join(part for part in key.split(',') if not part.startswith('depth='))
                    if key in variants and variants[key]!=variant:raise ValueError('Non-identical diagonal depth variants')
                    variants[key]=variant
                value['variants']=variants
            if relative.startswith('models/item/'):

                if name!='programmable_porthole_wall':
                    # Solid wall items do not use their inherited glass slot.
                    value['textures'].pop('glass', None)
                else:
                    value['textures']['glass']='vandorlabs:glass-palette/clear'
            target = OUT / 'assets/vandorlabs' / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(json.dumps(value, indent=2) + '\n')
        recipe=json.loads((SOURCE/('recipes/'+name+'.json')).read_text())
        recipe['result']['id']=recipe['result'].pop('item')
        target=OUT/('data/vandorlabs/recipe/'+name+'.json');target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(recipe)+'\n')
        loot={'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'vandorlabs:'+name,'functions':[{'function':'minecraft:copy_components','source':'block_entity','include':['minecraft:block_entity_data']}]}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]}
        target=OUT/('data/vandorlabs/loot_table/blocks/'+name+'.json');target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(loot)+'\n')
    print('Native panel assets: 56 canonical variants, current item geometry; existing housing/metal atlas reused')


if __name__ == '__main__':
    main()
