#!/usr/bin/env python3
"""Adapt current authored mounted-control assets without regenerating their geometry."""
import json
from pathlib import Path
import shutil
from PIL import Image

FAMILIES=('push_button','rocker_switch','compact_power_lever','industrial_power_lever','small_power_lever','large_power_lever','thruster_lever','airliner_throttle','fighter_throttle','wall_slider')
CANONICAL_ITEMS={'compact_power_lever':'industrial_power_lever','large_power_lever':'small_power_lever','airliner_throttle':'thruster_lever','fighter_throttle':'thruster_lever'}


def generate(root, out):
    assets=out/'assets/vandorlabs'
    models=set()
    textures=set()

    def write(path,value):
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(value,indent=2)+'\n')

    def texture(name):
        if not name.startswith('vandorlabs:'):return
        location=name.split(':',1)[1]
        if location in textures:return
        textures.add(location)
        source=None
        for directory in ('texture-packs/additional','texture-packs/default','src/main/resources'):
            candidate=root/directory/'assets/vandorlabs/textures'/(location+'.png')
            if candidate.is_file():source=candidate;break
        if source is None:raise ValueError('Missing mounted control texture: '+name)
        with Image.open(source) as image:
            if image.width!=image.height or image.width>128:raise ValueError('Unexpected authored control texture size: '+str(source))
        target=assets/'textures'/(location+'.png');target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
        metadata=source.with_suffix('.png.mcmeta')
        if metadata.is_file():shutil.copyfile(metadata,target.with_suffix('.png.mcmeta'))

    def model(name):
        if ':' not in name:name='minecraft:'+name
        namespace,location=name.split(':',1)
        if namespace!='vandorlabs':return name
        if not location.startswith(('block/','item/')):location='block/'+location
        if location not in models:
            models.add(location)
            source=root/'src/main/resources/assets/vandorlabs/models'/(location+'.json')
            value=json.loads(source.read_text())
            if 'parent' in value:value['parent']=model(value['parent'])
            for referenced in value.get('textures',{}).values():texture(referenced)
            write(assets/'models'/(location+'.json'),value)
        return namespace+':'+location

    for name in FAMILIES:
        state=json.loads((root/'src/main/resources/assets/vandorlabs/blockstates'/(name+'.json')).read_text())
        for variant in state['variants'].values():variant['model']=model(variant['model'])
        write(assets/'blockstates'/(name+'.json'),state)
        model('vandorlabs:item/'+name)
        item=CANONICAL_ITEMS.get(name,name)
        if item==name:
            recipe=json.loads((root/'src/main/resources/assets/vandorlabs/recipes'/(name+'.json')).read_text())
            recipe['result']['id']=recipe['result'].pop('item')
            write(out/'data/vandorlabs/recipe'/(name+'.json'),recipe)
        write(out/'data/vandorlabs/loot_table/blocks'/(name+'.json'),{
            'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'vandorlabs:'+item,
            'functions':[{'function':'minecraft:copy_components','source':'block_entity','include':['minecraft:custom_data']}]}],
            'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    print('Native mounted controls:',len(models),'canonical models,',len(textures),'unchanged authored textures')
    return [dict(type='minecraft:single',resource='vandorlabs:'+name) for name in sorted(textures)]
