#!/usr/bin/env python3
"""Verify native/slab coupler face ownership, UV density, states and projected icon padding."""
import itertools,json,math,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];ASSETS=ROOT/'src/main/resources/assets/vandorlabs'
subprocess.run([sys.executable,str(ROOT/'tools/generate_coupler_slab.py'),'--check'],check=True)

def read(kind,name):return json.loads((ASSETS/kind/(name+'.json')).read_text())
for state in ('off','on'):
 model=read('models/block','square_coupler_head_'+state);shaft=next(e for e in model['elements'] if e['name']=='sliding_shaft');back=next(e for e in model['elements'] if e['name']=='head_back')
 assert shaft['from'][2]==back['from'][2] and 'north' not in shaft['faces'],'Duplicate overlapping head front must not be submitted'
for half,offset in (('bottom',0),('top',8)):
 for name in ('base','head_off','head_on','released','locked'):
  source=read('models/block','square_coupler_'+name);slab=read('models/block','vehicle_coupler_slab_'+name+'_'+half)
  assert len(source['elements'])==len(slab['elements'])
  for a,b in zip(source['elements'],slab['elements']):
   for end in ('from','to'):
    assert b[end][1]==a[end][1]*.5+offset and b[end][0]==a[end][0] and b[end][2]==a[end][2]
    assert offset<=b[end][1]<=offset+8
   assert a['faces'].keys()==b['faces'].keys()
   for face,original in a['faces'].items():
    current=b['faces'][face];assert current['texture']==original['texture']
    if face in ('up','down'):assert current['uv']==original['uv'];continue
    axis=0 if original.get('rotation',0)%180 else 1
    for component in (0,1):
     scale=.5 if component==axis else 1
     assert abs((current['uv'][component+2]-current['uv'][component])-(original['uv'][component+2]-original['uv'][component])*scale)<1e-10
variants=read('blockstates','vehicle_coupler_slab')['variants'];assert len(variants)==24
for facing,half,locked in itertools.product(('north','east','south','west'),('bottom','top'),('false','true')):assert f'facing={facing},half={half},locked={locked}' in variants
for item in ('square_coupler','vehicle_coupler_slab'):
 data=read('models/item',item);model=read('models/block',data['parent'].split(':',1)[1].removeprefix('block/'));gui=data['display']['gui'];bounds=[]
 for element in model['elements']:
  for x,y,z in itertools.product(*zip(element['from'],element['to'])):
   x-=8;y-=8;z-=8;rx,ry,rz=map(math.radians,gui['rotation']);x,z=x*math.cos(ry)+z*math.sin(ry),-x*math.sin(ry)+z*math.cos(ry);y=y*math.cos(rx)-z*math.sin(rx);bounds.append((x*gui['scale'][0]+gui['translation'][0],y*gui['scale'][1]+gui['translation'][1]))
 assert max(abs(value) for point in bounds for value in point)<6.5,(item,bounds)
print('Coupler models PASS full front duplicate removed;24slab states half-height nativeUVdensity preserved;both icons padded after projection')
