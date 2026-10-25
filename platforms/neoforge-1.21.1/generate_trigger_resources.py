"""Native target paths for the existing programmable-trigger cube and recipe."""
import json


def generate(root,resources):
    assets=resources/'assets/vandorlabs'
    def write(path,data):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,indent=2)+'\n')
    states=json.loads((assets/'blockstates/programmable_block.json').read_text())
    for value in states['variants'].values():value['model']='vandorlabs:block/programmable_trigger_block'
    write(assets/'blockstates/programmable_trigger_block.json',states)
    write(assets/'models/block/programmable_trigger_block.json',json.loads((assets/'models/block/programmable_block.json').read_text()))
    item=json.loads((assets/'models/item/programmable_block.json').read_text());item['parent']='vandorlabs:block/programmable_trigger_block';write(assets/'models/item/programmable_trigger_block.json',item)
    recipe=json.loads((root/'src/main/resources/assets/vandorlabs/recipes/programmable_trigger_block.json').read_text());recipe['result']['id']=recipe['result'].pop('item');write(resources/'data/vandorlabs/recipe/programmable_trigger_block.json',recipe)
    loot=json.loads((resources/'data/vandorlabs/loot_table/blocks/programmable_block.json').read_text())
    loot=json.loads(json.dumps(loot).replace('vandorlabs:programmable_block','vandorlabs:programmable_trigger_block'))
    write(resources/'data/vandorlabs/loot_table/blocks/programmable_trigger_block.json',loot)
    for tag in ('mineable/pickaxe','needs_stone_tool'):
        path=resources/f'data/minecraft/tags/block/{tag}.json';data=json.loads(path.read_text());data['values']=list(dict.fromkeys(data['values']+['vandorlabs:programmable_trigger_block']));write(path,data)
