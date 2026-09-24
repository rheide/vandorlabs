#!/usr/bin/env python3
"""Set the four pedestal Bridge Chair heights from their original geometry."""
import json
from pathlib import Path

root=Path(__file__).resolve().parents[1]
assets=root/'generated-resources/assets/vandorlabs'
base_seat={'command':7.0,'companion':7.0,'operator':7.0,'conference':7.6}
raise_by={'command':2,'companion':1,'operator':2,'conference':1}
base_height={'command':22,'companion':20,'operator':21,'conference':22.5}
for role, original_seat in base_seat.items():
    name='bridge_chair_'+role
    path=assets/'models/block'/(name+'.json')
    model=json.loads(path.read_text())
    seat=next(e for e in model['elements'] if e.get('name')=='seat_shell')
    delta=original_seat+raise_by[role]-seat['from'][1]
    if delta:
        for element in model['elements']:
            part=element.get('name')
            if part in ('square_foot','cross_base_x','cross_base_z'):continue
            if part=='pedestal':element['to'][1]+=delta
            else:
                element['from'][1]+=delta
                element['to'][1]+=delta
        path.write_text(json.dumps(model,indent=2)+'\n')
p=assets/'data/blocks.json'
catalog=json.loads(p.read_text())
for entry in catalog:
    for role,height in base_height.items():
        if entry['id']=='bridge_chair_'+role:
            entry['height_units']=height+raise_by[role]
            entry['seat_y_units']=10+raise_by[role]
p.write_text(json.dumps(catalog,indent=2)+'\n')
# Dynmap's fallback geometry is clipped into the two actual block cells.
p=root/'src/main/resources/assets/vandorlabs/dynmap-models.txt'
names={'bridge_chair_'+role for role in base_seat}
lines=[line for line in p.read_text().splitlines()
       if not any('id=%'+name+',' in line for name in names)]
face=':e/0:u/0:n/0:s/0:d/0:w/0'
for name in sorted(names):
    model=json.loads((assets/'models/block'/(name+'.json')).read_text())
    for facing,rotation in (('south',''),('west',':R/0/90/0'),
                            ('north',':R/0/180/0'),('east',':R/0/270/0')):
        for upper in (False,True):
            shift=16 if upper else 0
            boxes=[]
            for element in model['elements']:
                x0,y0,z0=element['from'];x1,y1,z1=element['to']
                lo=max(0,y0-shift);hi=min(16,y1-shift)
                if hi<=lo:continue
                boxes.append(',box='+f'{x0:g}/{lo:g}/{z0:g}:{x1:g}/{hi:g}/{z1:g}'+face+rotation)
            lines.append('modellist:id=%'+name+',state=facing:'+facing+'/upper:'+str(upper).lower()+''.join(boxes))
p.write_text('\n'.join(lines)+'\n')
