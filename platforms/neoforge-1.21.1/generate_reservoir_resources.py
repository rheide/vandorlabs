#!/usr/bin/env python3
"""Native own-storage assets from the existing Source artwork and crafting definitions."""
from pathlib import Path
import json, shutil, sys
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
OUT=Path(__file__).resolve().parent/'build/generated/reservoir-resources'
sys.path.insert(0,str(ROOT/'tools'))
from compact_runtime_assets import resized

def write(name,value):
 p=OUT/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,indent=2)+'\n')
def main():
 if OUT.exists():shutil.rmtree(OUT)
 names=['matter/casing','matter/energy_in','matter/energy_out','matter/port','matter/tank_empty','imported/power/battery_cell_bank']
 for name in names:
  source=ROOT/f'texture-packs/additional/assets/vandorlabs/textures/blocks/{name}.png';target=OUT/f'assets/vandorlabs/textures/blocks/{name}.png';target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target);resized(target,128)
 target=OUT/'assets/vandorlabs/textures/blocks/reservoir/white.png';target.parent.mkdir(parents=True,exist_ok=True);Image.new('RGBA',(16,16),(255,255,255,255)).save(target)
 display={'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.55,.55,.55]},'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[.25,.25,.25]},'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[.5,.5,.5]},'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.375,.375,.375]},'thirdperson_lefthand':{'rotation':[75,225,0],'translation':[0,2.5,0],'scale':[.375,.375,.375]},'firstperson_righthand':{'rotation':[0,45,0],'translation':[0,0,0],'scale':[.4,.4,.4]},'firstperson_lefthand':{'rotation':[0,225,0],'translation':[0,0,0],'scale':[.4,.4,.4]}}
 for name in ['battery_block','hexagonal_fuel_tank']:
  write(f'assets/vandorlabs/blockstates/{name}.json',{'variants':{f'facing={side}':{'model':f'vandorlabs:block/{name}'} for side in ['down','up','north','south','west','east']}})
  write(f'assets/vandorlabs/models/block/{name}.json',{'textures':{'particle':'vandorlabs:blocks/matter/casing'},'elements':[]})
  write(f'assets/vandorlabs/models/item/{name}.json',{'parent':'minecraft:builtin/entity','textures':{'particle':'vandorlabs:blocks/matter/casing'},'display':display})
  recipe=json.loads((ROOT/f'src/main/resources/assets/vandorlabs/recipes/{name}.json').read_text());recipe['result']={'id':recipe['result']['item'],'count':recipe['result'].get('count',1)};write(f'data/vandorlabs/recipe/{name}.json',recipe)
if __name__=='__main__':main()
