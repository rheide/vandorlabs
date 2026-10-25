"""Check native imported part reconstruction and complete animation envelopes."""
from collections import Counter
from pathlib import Path
import json,math
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/vandorlabs'
specs=json.loads((ASSETS/'data/frame_machines.json').read_text())
def faces(path):
 positions=[];uv=[];result=[]
 for line in path.read_text().splitlines():
  p=line.split()
  if not p:continue
  if p[0]=='v':positions.append(tuple(map(float,p[1:4])))
  elif p[0]=='vt':uv.append(tuple(map(float,p[1:3])))
  elif p[0]=='f':result.append(tuple((positions[int(v.split('/')[0])-1],uv[int(v.split('/')[1])-1]) for v in p[1:]))
 return result
checked=0
for spec in specs:
 if spec['design'] not in ['oculus_drive','sentinel_array','standalone_antenna','standalone_dish','survey_dish']:continue
 assert spec['chip_anchor'] and len(spec['state_models'])==3
 assert spec['state_labels'][-1] in ['Active','Scanning']
 root=ASSETS/'models/block'/spec['model']
 for state in spec['state_models']:
  complete=root/state;parts=sorted((complete.parent/'parts').glob('*.obj'))
  assert Counter(faces(complete))==Counter(face for part in parts for face in faces(part)),complete
  for part in parts:
   for face in faces(part):
    for position,_ in face:
     for step in range(81):
      x,y,z=position
      if part.stem=='rotor':
       px,_,pz=spec['motion_pivot'];a=math.radians(-55+110*step/80);x,z=px+(x-px)*math.cos(a)+(z-pz)*math.sin(a),pz-(x-px)*math.sin(a)+(z-pz)*math.cos(a)
      elif part.stem.startswith('shutter_'):y+=(1 if part.stem=='shutter_upper' else -1)*.5125*step/80
      assert all(-.001<=v<=limit+.001 for v,limit in zip((x,y,z),(spec['width'],spec['height'],spec['depth']))),(part,position,step)
   checked+=1
print('Oculus/radar parts PASS',checked,'state/part meshes reconstruct complete exports, native chip anchors, swept shutter and dish containment')
