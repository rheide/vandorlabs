#!/usr/bin/env python3
"""Derive Battery Module cassette variants from the established padded rack meshes."""
import argparse
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
CHECK=argparse.ArgumentParser(description=__doc__)
CHECK.add_argument('--check',action='store_true')
CHECK=CHECK.parse_args().check
def write(path,text):
    if CHECK:
        assert path.is_file() and path.read_text()==text, str(path)
    else:path.write_text(text)
BASE=ROOT/'src/main/resources/assets/vandorlabs/models/block/module_rack'
for name in ['blank_module']+['slot_'+str(i) for i in range(4)]+['slot_seated_'+str(i) for i in range(4)]:
    source=json.loads((BASE/(name+'.json')).read_text())
    for level in range(5):
        data=json.loads(json.dumps(source));body=next(part for part in data['elements'] if part.get('name')=='cassette_body');y=body['from'][1];z=body['to'][2]+.025
        data['textures']['bar_off']='vandorlabs:blocks/resource_ports/power_in_off'
        data['textures']['bar_on']='vandorlabs:blocks/resource_ports/power_in_on'
        def rect(label,x1,y1,x2,y2,texture,uv,depth=z):
            data['elements'].append({'name':label,'from':[x1,y1,depth],'to':[x2,y2,depth+.001],'shade':False,'faces':{'south':{'texture':'#'+texture,'uv':uv}}})
        # One source texel remains one texel: x14..49,y7..10, with no copied/repainted bitmap.
        rect('power_port_bar_off',2.5,y+1,11.5,y+2,'bar_off',[3.5,1.75,12.5,2.75])
        if level:
            fill=level*9
            rect('power_port_bar_on',2.5,y+1,2.5+fill/4,y+2,'bar_on',[3.5,1.75,3.5+fill/4,2.75],z+.003)
        target='battery_module' if name=='blank_module' else name.replace('slot_seated_','slot_seated_battery_') if 'seated' in name else name.replace('slot_','slot_battery_')
        write(BASE/(target+'_'+str(level)+'.json'),json.dumps(data,separators=(',',':'))+'\n')
write(BASE/'battery_module.json','{"parent":"vandorlabs:block/module_rack/battery_module_0"}\n')

# A single full on-bar per bay supplies clipped overlays to both platform renderers.
for slot in range(4):
    bottom=11.75-slot*3.5
    overlay={'ambientocclusion':False,'textures':{'bar':'vandorlabs:blocks/resource_ports/power_in_on'},'elements':[{'from':[2.5,bottom+1,15.278],'to':[11.5,bottom+2,15.279],'shade':False,'faces':{'south':{'texture':'#bar','uv':[3.5,1.75,12.5,2.75]}}}]}
    write(BASE/('battery_bar_'+str(slot)+'.json'),json.dumps(overlay,separators=(',',':'))+'\n')
# Item-only variants share the body and retain the exact same raw off/on bar texels.
source=json.loads((BASE/'battery_module_0.json').read_text())
for fill in range(37):
    item=json.loads(json.dumps(source))
    if fill:item['elements'].append({'from':[2.5,7.5,15.278],'to':[2.5+fill/4,8.5,15.279],'shade':False,'faces':{'south':{'texture':'#bar_on','uv':[3.5,1.75,3.5+fill/4,2.75]}}})
    write(BASE/('battery_item_'+str(fill)+'.json'),json.dumps(item,separators=(',',':'))+'\n')
item={'parent':'vandorlabs:block/module_rack/battery_item_0','overrides':[{'predicate':{'vandorlabs:charge':n/36},'model':'vandorlabs:block/module_rack/battery_item_'+str(n)} for n in range(1,37)]}
write(ROOT/'src/main/resources/assets/vandorlabs/models/item/battery_module.json',json.dumps(item,separators=(',',':'))+'\n')
