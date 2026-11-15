"""Native programmable plate uses the exact thin authored model and the shared material atlas."""
import json

def generate(root,out):
    source=root/'src/main/resources/assets/vandorlabs'
    def write(relative,value):
        path=out/relative;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n')
    states={f'power={power}':{'model':'vandorlabs:block/programmable_pressure_plate_'+('up' if power==0 else 'down')} for power in range(16)}
    write('assets/vandorlabs/blockstates/programmable_pressure_plate.json',{'variants':states})
    for suffix in ('up','down'):
        value=json.loads((source/f'models/block/programmable_pressure_plate_{suffix}.json').read_text())
        write(f'assets/vandorlabs/models/block/programmable_pressure_plate_{suffix}.json',value)
    write('assets/vandorlabs/models/item/programmable_pressure_plate.json',json.loads((source/'models/item/programmable_pressure_plate.json').read_text()))
    recipe=json.loads((source/'recipes/programmable_pressure_plate.json').read_text());recipe['result']['id']=recipe['result'].pop('item');recipe['neoforge:conditions']=[{'type':'neoforge:item_exists','item':'vandorlabs:programmable_pressure_plate'}]
    write('data/vandorlabs/recipe/programmable_pressure_plate.json',recipe)
    write('data/vandorlabs/loot_table/blocks/programmable_pressure_plate.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'vandorlabs:programmable_pressure_plate','functions':[{'function':'minecraft:copy_components','source':'block_entity','include':['minecraft:custom_data']}]}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    return []
