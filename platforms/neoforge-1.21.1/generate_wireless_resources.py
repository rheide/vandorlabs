#!/usr/bin/env python3
"""Adapt the current directional endpoint cubes and padded item models unchanged."""
import json
import shutil
from PIL import Image


def generate(root, out):
    assets=out/'assets/vandorlabs'
    sprites=[]
    def write(path,value):
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(value,indent=2)+'\n')
    for kind in ('transmitter','receiver'):
        name='redstone_'+kind
        block=name+'_block'
        state=json.loads((root/'src/main/resources/assets/vandorlabs/blockstates'/(block+'.json')).read_text())
        for variant in state['variants'].values():variant['model']='vandorlabs:block/'+name
        write(assets/'blockstates'/(block+'.json'),state)
        model=json.loads((root/'src/main/resources/assets/vandorlabs/models/block'/(name+'.json')).read_text());model['parent']='minecraft:block/cube'
        write(assets/'models/block'/(name+'.json'),model)
        item=json.loads((root/'src/main/resources/assets/vandorlabs/models/item'/(block+'.json')).read_text());write(assets/'models/item'/(block+'.json'),item)
        for face in ('up','down','left','right','port','type'):
            location='blocks/'+name+'/'+face
            source=root/'texture-packs/additional/assets/vandorlabs/textures'/(location+'.png')
            with Image.open(source) as image:
                if image.size!=(128,128):raise ValueError('Unexpected authored endpoint texture size: '+str(source))
            target=assets/'textures'/(location+'.png');target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
            metadata=source.with_suffix('.png.mcmeta')
            if metadata.is_file():shutil.copyfile(metadata,target.with_suffix('.png.mcmeta'))
            sprites.append(dict(type='minecraft:single',resource='vandorlabs:'+location))
        # Legacy ordinary pick/drop does not retain the endpoint's world configuration.
        write(out/'data/vandorlabs/loot_table/blocks'/(block+'.json'),{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'vandorlabs:'+block}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    print('Native wireless endpoints: two exact authored cubes, padded items and twelve unchanged 128px sprites')
    return sprites
