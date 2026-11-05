"""Current slab/stair recipes and configured drops; render resources use their own adapter."""
import json

def generate(root,out):
    def write(path,data):
        target=out/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(data,separators=(',',':'))+'\n')
    for name in ['programmable_slab','programmable_stairs']:
        recipe=json.loads((root/'src/main/resources/assets/vandorlabs/recipes'/(name+'.json')).read_text())
        recipe['result']['id']=recipe['result'].pop('item')
        write('data/vandorlabs/recipe/'+name+'.json',recipe)
        write('data/vandorlabs/loot_table/blocks/'+name+'.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'vandorlabs:'+name,'functions':[{'function':'minecraft:copy_components','source':'block_entity','include':['minecraft:block_entity_data']}]}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
