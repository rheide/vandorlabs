#!/usr/bin/env python3
"""Import six native 64px appliance face sets without moving existing material IDs."""
import argparse,json,shutil
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
IDS=['indoor_ac_slab_dark_cyan','indoor_ac_slab_silver','outdoor_ac_dark_cyan','outdoor_ac_silver','printer','scanner']
LABELS=['Indoor AC (Dark Cyan)','Indoor AC (Silver)','Outdoor AC (Dark Cyan)','Outdoor AC (Silver)','Printer','Scanner']
FACES=['bottom','top','front','back','left','right']
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('source',type=Path);a=p.parse_args()
 assets=ROOT/'src/main/resources/assets/vandorlabs';catalog=ROOT/'generated-resources/assets/vandorlabs/data/unified_textures.json';entries=json.loads(catalog.read_text());existing={e['id']:e for e in entries}
 def write(path,data):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,indent=2)+'\n')
 for index,(identifier,label) in enumerate(zip(IDS,LABELS)):
  tex={}
  for face in FACES:
   source=a.source/identifier/(face+'.png')
   with Image.open(source) as im:
    if im.size!=(64,64):raise ValueError(f'Expected native 64px face: {source}')
   target=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks/appliances'/identifier/(face+'.png');target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
   tex[face]='vandorlabs:blocks/appliances/'+identifier+'/'+face
   entry={'id':'appliance_'+identifier+'_'+face,'label':label+' '+face.title(),'category':'Devices','menuGroup':label,'source':'appliances/'+identifier+'/'+face}
   if entry['id'] in existing:existing[entry['id']].update(entry)
   else:entries.append(entry)
  tex['particle']=tex['front'];faces={direction:{'texture':'#'+face,'uv':[0,0,16,16]} for direction,face in zip(['down','up','north','south','west','east'],FACES)}
  write(assets/'models/block/appliances'/f'{identifier}.json',{'textures':tex,'elements':[{'from':[0,0,0],'to':[16,8 if index<2 else 16,16],'faces':faces}]})
  write(assets/'models/item/appliances'/f'{identifier}.json',{'parent':'vandorlabs:block/appliances/'+identifier,'display':{'gui':{'rotation':[30,225,0],'scale':[.5,.5,.5]}}})
  if index>=4:
   write(assets/'blockstates'/f'{identifier}.json',{'variants':{'facing='+face:{'model':'vandorlabs:appliances/'+identifier,'y':turn*90} for turn,face in enumerate(['north','east','south','west'])}})
   write(assets/'models/item'/f'{identifier}.json',{'parent':'vandorlabs:item/appliances/'+identifier})
 write(catalog,entries)
 lang=ROOT/'generated-resources/assets/vandorlabs/lang/en_us.lang';lines=lang.read_text().splitlines();keys={'tile.vandorlabs.'+key+'.name':label for key,label in zip(IDS,LABELS)};lines=[line for line in lines if line.split('=',1)[0] not in keys];lines+=['']+[key+'='+label for key,label in keys.items()];lang.write_text('\n'.join(lines)+'\n')
 print('Imported36native64pxfaces,4programmablepresetsand2standalonedecorations')
if __name__=='__main__':main()
