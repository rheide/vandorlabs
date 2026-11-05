#!/usr/bin/env python3
"""Package the four existing handheld icons under one native item registry ID."""
import json
from pathlib import Path
import shutil
ROOT=Path(__file__).resolve().parents[2]
OUT=Path(__file__).resolve().parent/'build/generated/transmitter-resources'
ASSETS=OUT/'assets/vandorlabs'
for directory in ('models/item','textures/item'):(ASSETS/directory).mkdir(parents=True,exist_ok=True)
variants=('short','medium','long','infinite')
for name in variants:
    file='redstone_transmitter_'+name
    shutil.copyfile(ROOT/'texture-packs/additional/assets/vandorlabs/textures/items'/(file+'.png'),ASSETS/'textures/item'/(file+'.png'))
    (ASSETS/'models/item'/(file+'.json')).write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':'vandorlabs:item/'+file},'display':{'gui':{'scale':[0.875,0.875,0.875]}}},separators=(',',':'))+'\n')
model={'parent':'vandorlabs:item/redstone_transmitter_short','overrides':[{'predicate':{'custom_model_data':tier},'model':'vandorlabs:item/redstone_transmitter_'+variants[tier]} for tier in range(1,4)]}
(ASSETS/'models/item/redstone_transmitter.json').write_text(json.dumps(model,separators=(',',':'))+'\n')

# Keep the canonical patterns/ingredients; replace legacy metadata with native components.
recipes=OUT/'data/vandorlabs/recipe';recipes.mkdir(parents=True,exist_ok=True)
for tier,name in enumerate(variants):
    file='redstone_transmitter_'+name
    recipe=json.loads((ROOT/'src/main/resources/assets/vandorlabs/recipes'/(file+'.json')).read_text())
    result=recipe['result'];result['id']=result.pop('item')
    if result.pop('data')!=tier:raise ValueError('Unexpected transmitter tier in canonical recipe')
    result['components']={'minecraft:custom_data':'{TransmitterTier:%d}' % tier,'minecraft:custom_model_data':tier}
    (recipes/(file+'.json')).write_text(json.dumps(recipe,indent=2)+'\n')
