#!/usr/bin/env python3
"""Analytic yaw extrema prove fitted observer geometry never leaves reserved cells."""
import math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/vandorlabs/models/block/radar'
checks=0
for name,width,height,count in [('standalone_dish',3,3,145),('standalone_antenna',2,4,114)]:
 for state in ['off','idle','scanning']:
  parts=list((ROOT/name/state/'parts').glob('*.obj'));points=[];mount=[];faces=0
  for part in parts:
   vertices=[];mount_ids=set();group=''
   for line in part.read_text().splitlines():
    p=line.split()
    if not p:continue
    if p[0]=='v':vertices.append(tuple(map(float,p[1:4])))
    elif p[0]=='vn':assert abs(sum(float(v)**2 for v in p[1:4])-1)<1e-8
    elif p[0]=='g':group=p[1]
    elif p[0]=='f':
     faces+=1
     if '__standalone_mount__' in group:mount_ids.update(int(v.split('/')[0])-1 for v in p[1:])
   mount.extend(vertices[i] for i in mount_ids)
   for x,y,z in vertices:
    angles=[0.0]
    if part.stem=='rotor':
     angles=[math.radians(-55),math.radians(55)];dx=x-1.5;dz=z-1.5
     for stationary in [math.atan2(dz,dx),math.atan2(-dx,dz)]:
      for k in range(-2,3):
       value=stationary+k*math.pi
       if math.radians(-55)<=value<=math.radians(55):angles.append(value)
    for angle in angles:
     px,pz=(1.5+(x-1.5)*math.cos(angle)+(z-1.5)*math.sin(angle),1.5-(x-1.5)*math.sin(angle)+(z-1.5)*math.cos(angle)) if part.stem=='rotor' else (x,z)
     points.append((px,y,pz));assert .015<=px<=width-.015 and .015<=pz<=width-.015 and 0<=y<=height;checks+=1
  assert faces==count,'No geometry growth'
  assert max(p[0] for p in mount)-min(p[0] for p in mount)>=width*.93
  assert max(p[2] for p in mount)-min(p[2] for p in mount)>=width*.93
  for size in ([3,4,5,6,16] if width==3 else [2,3,4,8]):
   scale=size/width;h=size if width==3 else 2*size
   for turn in range(4):
    for inverted in [False,True]:
     for x,y,z in points:
      x,y,z=x*scale,y*scale,z*scale
      for _ in range(turn):x,z=size-z,x
      if inverted:y=h-y
      assert 0<=x<=size and 0<=z<=size and 0<=y<=h;checks+=1
print('Observer fitted containment PASS checks=',checks,'analytic +/-55 yaw extrema all states/facings/inversions/sizes; widened mounts; unchanged145/114quads')
