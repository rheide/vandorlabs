#!/usr/bin/env python3
"""Derive the larger wheel and rendering groups from the two authored gear models."""
import copy
import json
from pathlib import Path
assets=Path(__file__).resolve().parents[1]/'src/main/resources/assets/vandorlabs/models'
def read(size): return json.loads((assets/f'block/landing_gear_{size}_retracted.json').read_text())
def write(path,data): path.write_text(json.dumps(data,indent=2)+'\n')
large=copy.deepcopy(read('medium'))
# A 14-pixel wheel (Medium is 12), wider tire/forks, and a full 16-pixel mount/axle.
wheel_y=[(0,1),(1,2),(2,4),(4,10),(10,12),(12,13),(13,14)]
wheel_z=[(6,10),(4,12),(2,14),(1,15),(2,14),(4,12),(6,10)]
parts={
 'mount_plate':([0,15,0],[16,16,16]),
 'piston':([6,14.5,6],[10,15,10]),
 'fork_bridge':([2,14,6],[14,15,10]),
 'fork_left':([2,6,6],[4,14,10]),
 'fork_right':([12,6,6],[14,14,10]),
 'axle_cap_left':([0,6,7],[2,8,9]),
 'axle_cap_right':([14,6,7],[16,8,9]),
}
for e in large['elements']:
 name=e['name']
 if name.startswith('wheel_band_'):
  i=int(name.rsplit('_',1)[1])-1;y0,y1=wheel_y[i];z0,z1=wheel_z[i]
  e['from'],e['to']=[4,y0,z0],[12,y1,z1]
 else:e['from'],e['to']=parts[name]
write(assets/'block/landing_gear_large_retracted.json',large)
extra_large=copy.deepcopy(large)
for e in extra_large['elements']:
 for key in ['from','to']:
  x,y,z=e[key]
  e[key]=[2*x-8,2*y-16,2*z-8]
write(assets/'block/landing_gear_extra_large_retracted.json',extra_large)
for size in ['small','medium','large','extra_large']:
 model=read(size)
 model['textures']['arm']='vandorlabs:blocks/programmable_glass/metal_side'
 for e in model['elements']:
  if e['name']=='piston':
   e['from'][0]=e['from'][2]=4 if size=='extra_large' else 6
   e['to'][0]=e['to'][2]=12 if size=='extra_large' else 10
   for face,values in e['faces'].items():
    values['texture']='#arm'
    values['uv']=[4,4,12,12] if size=='extra_large' and face in ['up','down'] else [6,6,10,10] if face in ['up','down'] else [4,0,12,e['to'][1]-e['from'][1]] if size=='extra_large' else [6,0,10,e['to'][1]-e['from'][1]]
 write(assets/f'block/landing_gear_{size}_retracted.json',model)
 for group in ['fixed','wheel','piston']:
  data=copy.deepcopy(model)
  def category(e): return 'piston' if e['name']=='piston' else 'fixed' if e['name'] in ['mount_plate','fixed_sleeve'] else 'wheel'
  data['elements']=[e for e in data['elements'] if category(e)==group]
  write(assets/f'item/landing_gear_{size}_{group}.json',data)
 item={'parent':f'vandorlabs:block/landing_gear_{size}_retracted'}
 if size=='extra_large':
  # The placed model spans 32 pixels; shrink and recenter only its inventory view.
  item['display']={'gui':{'rotation':[30,225,0],
                          'translation':[0,2.5,0],
                          'scale':[0.3125,0.3125,0.3125]}}
 write(assets/f'item/landing_gear_{size}.json',item)
write(assets/'item/landing_gear.json',{'parent':'vandorlabs:item/landing_gear_small'})
