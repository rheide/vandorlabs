#!/usr/bin/env python3
"""Fit observer bodies and mounting plates inside their unchanged reserved footprints."""
import json,math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
STAMP='# Vandor observer footprint fit v1'
def fit_file(path,dish):
 lines=path.read_text().splitlines()
 if STAMP in lines:return False
 vertices=[];mount=set();body=set();group=''
 for line in lines:
  p=line.split()
  if not p:continue
  if p[0]=='v':vertices.append(tuple(map(float,p[1:4])))
  elif p[0]=='g':group=p[1]
  elif p[0]=='f':(mount if '__standalone_mount__' in group else body).update(int(v.split('/')[0])-1 for v in p[1:])
 assert not mount.intersection(body),'A mount/body seam needs distinct vertex transforms'
 output=[STAMP];index=0
 for line in lines:
  p=line.split()
  if p and p[0]=='v':
   x,y,z=vertices[index]
   if dish:
    factor=2.875/1.625 if index in mount else 1.08;x=1.5+(x-1.5)*factor;z=1.5+(z-1.5)*factor;y*=1.08
   else:
    x=1+(x-1)*(1.875 if index in mount else 1.03);z=1+(z-1.1875)*(1.875/1.125 if index in mount else 1.03);y*=1.2
   output.append('v %.10f %.10f %.10f'%(x,y,z));index+=1
  elif p and p[0]=='vn':
   x,y,z=map(float,p[1:4]);x/=1.08 if dish else 1.03;y/=1.08 if dish else 1.2;z/=1.08 if dish else 1.03;length=math.sqrt(x*x+y*y+z*z);output.append('vn %.10f %.10f %.10f'%(x/length,y/length,z/length))
  else:output.append(line)
 path.write_text('\n'.join(output)+'\n');return True

def fit():
 changed=0
 for name in ['standalone_dish','standalone_antenna']:
  for path in (ROOT/'src/main/resources/assets/vandorlabs/models/block/radar'/name).rglob('*.obj'):changed+=fit_file(path,name=='standalone_dish')
 catalog=ROOT/'src/main/resources/assets/vandorlabs/data/frame_machines.json';data=json.loads(catalog.read_text())
 for spec in data:
  if spec['design']=='standalone_dish':spec['motion_pivot'][1]=.135
 catalog.write_text(json.dumps(data,indent=2)+'\n')
 print('Fitted observer meshes:',changed,'files; Dish8percentbody/2.875base, Array1.03XZ/1.2Y/1.875base')
if __name__=='__main__':fit()
