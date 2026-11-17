#!/usr/bin/env python3
"""Validate appliance face catalogs, orientations and inventory padding against authored assets."""
import json,math,re
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
IDS=['indoor_ac_slab_dark_cyan','indoor_ac_slab_silver','outdoor_ac_dark_cyan','outdoor_ac_silver','printer','scanner']
FACES=['bottom','top','front','back','left','right']
entries=json.loads((ROOT/'generated-resources/assets/vandorlabs/data/unified_textures.json').read_text());byid={e['id']:e for e in entries}
for index,identifier in enumerate(IDS):
 model=json.loads((ROOT/f'src/main/resources/assets/vandorlabs/models/block/appliances/{identifier}.json').read_text())
 assert model['elements'][0]['to']==[16,8 if index<2 else 16,16]
 for direction,face in zip(['down','up','north','south','west','east'],FACES):
  entry=byid['appliance_'+identifier+'_'+face];assert entry['source']==f'appliances/{identifier}/{face}'
  assert model['elements'][0]['faces'][direction]=={'texture':'#'+face,'uv':[0,0,16,16]}
  with Image.open(ROOT/f'texture-packs/additional/assets/vandorlabs/textures/blocks/{entry["source"]}.png') as art:assert art.size==(64,64)
 item=json.loads((ROOT/f'src/main/resources/assets/vandorlabs/models/item/appliances/{identifier}.json').read_text());assert item['display']['gui']['scale']==[.5,.5,.5]
 if index>=4:
  states=json.loads((ROOT/f'src/main/resources/assets/vandorlabs/blockstates/{identifier}.json').read_text())['variants'];assert [states['facing='+f]['y'] for f in ['north','east','south','west']]==[0,90,180,270]
ids=re.search(r'IDS=\{([^}]+)',(ROOT/'src/portable/java/com/vandorlabs/appliance/ApplianceCatalog.java').read_text())[1];assert re.findall(r'"([^"]+)"',ids)==IDS
print('Appliance assets PASS 36native64pxfaces full/slabshape completefaceUVs fourstaticrotations and paddedinventorytransforms')
