#!/usr/bin/env python3
"""Native static appliance models; all authored 64px faces keep one catalog texture owner."""
from pathlib import Path
import json,re,shutil
ROOT=Path(__file__).resolve().parents[2]
OUT=Path(__file__).resolve().parent/'build/generated/appliance-resources'
FACES=('bottom','top','front','back','left','right')
def catalog(root=ROOT):
 text=(root/'src/portable/java/com/vandorlabs/appliance/ApplianceCatalog.java').read_text()
 def strings(name):return re.findall(r'"([^"\n]+)"',re.search(r'\b'+name+r'\s*=\s*\{([^}]+)\}',text).group(1))
 ids,labels=strings('IDS'),strings('LABELS')
 if len(ids)!=6 or len(labels)!=6:raise ValueError('Appliance catalog shape')
 return list(zip(ids,labels))
def translations(root=ROOT):
 values={}
 for index,(name,label) in enumerate(catalog(root)):
  values[('item.vandorlabs.preset.' if index<4 else 'block.vandorlabs.')+name]=label
 return values
def generate():
 if OUT.exists():shutil.rmtree(OUT)
 assets=OUT/'assets/vandorlabs';source=ROOT/'src/main/resources/assets/vandorlabs'
 def write(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n')
 for index,(name,label) in enumerate(catalog()):
  model=json.loads((source/f'models/block/appliances/{name}.json').read_text())
  model.setdefault('parent','minecraft:block/block')
  prefix='vandorlabs:blocks/appliances/'+name+'/'
  model['textures']={key:('vandorlabs:housing/appliance_'+name+'_'+value[len(prefix):] if value.startswith(prefix) else value) for key,value in model['textures'].items()}
  write(assets/f'models/block/appliances/{name}.json',model)
  item=json.loads((source/f'models/item/appliances/{name}.json').read_text())
  write(assets/f'models/item/appliances/{name}.json',item)
  for face in FACES:
   path=ROOT/f'texture-packs/additional/assets/vandorlabs/textures/blocks/appliances/{name}/{face}.png'
   from PIL import Image
   with Image.open(path) as image:
    if image.size!=(64,64):raise ValueError('Expected authored64px face '+str(path))
  if index>=4:
   write(assets/f'blockstates/{name}.json',{'variants':{'facing='+face:{'model':'vandorlabs:block/appliances/'+name,'y':angle} for face,angle in [('north',0),('east',90),('south',180),('west',270)]}})
   write(assets/f'models/item/{name}.json',{'parent':'vandorlabs:item/appliances/'+name})
   write(OUT/f'data/vandorlabs/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'vandorlabs:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
 print('Native appliances:4configured previews +2horizontal decorative blocks,36authored64pxfaces')
if __name__=='__main__':generate()
