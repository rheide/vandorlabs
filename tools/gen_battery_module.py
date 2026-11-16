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
        data['textures']['power']='vandorlabs:blocks/resource_ports/power_in_icon'
        data['textures']['green']='minecraft:blocks/emerald_block'
        def rect(label,x1,y1,x2,y2,texture,uv):
            data['elements'].append({'name':label,'from':[x1,y1,z],'to':[x2,y2,z+.001],'shade':False,'faces':{'south':{'texture':'#'+texture,'uv':uv}}})
        for i,(x1,y1,x2,y2) in enumerate([(5.5,1.75,6.75,2.0),(5.25,1.25,6.0,1.75),(5.75,1.0,7.0,1.25),(6.5,.5,7.25,1.0)]):
            rect('power_lightning_'+str(i),x1,y+y1,x2,y+y2,'power',[4.5,4.5,4.75,4.75])
        for i in range(4):rect('charge_'+str(i),8+i*.7,y+.8,8.5+i*.7,y+1.7,'green' if i<level else 'dark',[4,4,5,5])
        target='battery_module' if name=='blank_module' else name.replace('slot_seated_','slot_seated_battery_') if 'seated' in name else name.replace('slot_','slot_battery_')
        write(BASE/(target+'_'+str(level)+'.json'),json.dumps(data,separators=(',',':'))+'\n')
write(BASE/'battery_module.json','{"parent":"vandorlabs:block/module_rack/battery_module_0"}\n')
