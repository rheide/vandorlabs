"""Preserve the authored standalone wheel models and footprints on the native platform."""
import json

def generate(root,out):
    source=root/'src/main/resources/assets/vandorlabs'
    def write(path,value):
        target=out/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(value,indent=2)+'\n')
    def remap(value):
        if isinstance(value,list):return [remap(v) for v in value]
        if isinstance(value,dict):return {k:remap(v) for k,v in value.items()}
        if isinstance(value,str):return value.replace('minecraft:blocks/','minecraft:block/').replace('vandorlabs:blocks/','vandorlabs:block/')
        return value
    for name in ('wheel_small','wheel_medium','wheel_large','wheel_empty'):
        model=remap(json.loads((source/f'models/block/{name}.json').read_text()))
        write(f'assets/vandorlabs/models/block/{name}.json',model)
        if name=='wheel_empty':continue
        states=json.loads((source/f'blockstates/{name}.json').read_text())
        for variant in states['variants'].values():variant['model']=variant['model'].replace('vandorlabs:','vandorlabs:block/')
        write(f'assets/vandorlabs/blockstates/{name}.json',states)
        item=remap(json.loads((source/f'models/item/{name}.json').read_text()))
        write(f'assets/vandorlabs/models/item/{name}.json',item)
        recipe=json.loads((source/f'recipes/{name}.json').read_text());recipe['result']['id']=recipe['result'].pop('item');recipe['neoforge:conditions']=[{'type':'neoforge:item_exists','item':'vandorlabs:'+name}]
        write(f'data/vandorlabs/recipe/{name}.json',recipe)
        write(f'data/vandorlabs/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'vandorlabs:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    return []
