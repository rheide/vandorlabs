#!/usr/bin/env python3
"""Compact an actual released-GL capture into pose and render-request contracts."""
import json
import math
from pathlib import Path
import struct

GETTERS=['SIGNAL','STATE','EFFECTIVE_MODE','SURFACE_0','SURFACE_1','MODE_0','MODE_1','SPEED_0','SPEED_1','OVERRIDES','SPLIT','PRIMARY_ARTWORK','ANIMATION_SPEED']

def compact(directory):
 directory=Path(directory);rows=[json.loads(line) for line in (directory/'screen-role-gl.jsonl').read_text().splitlines()];poses={};requests={}
 for row in rows:
  key=(row['id'],row['meta'],row['variant']);matrix=row['draws'][0]['modelview']
  if any(draw['modelview']!=matrix for draw in row['draws']):raise AssertionError('Role has multiple draw transforms: '+str(key))
  if key in poses and poses[key]!=matrix:raise AssertionError('Playback/frame changed role transform: '+str(key))
  poses[key]=matrix
  key=(row['id'],row['mode'],row['variant'],row['frame']);request=dict(getterMask=row['getterMask'],nestedGetterMask=row['nestedGetterMask'],draws=[{name:draw[name] for name in ['mode','vertexCount','texture','state']} for draw in row['draws']])
  if key in requests and requests[key]!=request:raise AssertionError('Role request depends on metadata: '+str(key))
  requests[key]=request
 if len(rows)!=1260 or len(poses)!=210 or len(requests)!=126:raise AssertionError('Released role contract coverage changed')
 return dict(schema=1,context='Released Forge 1.12.2 renderer; actual software GL matrices and draw state; GPU submission replaced with CPU capture',artifactSha256=(directory/'artifact.sha256').read_text().split()[0],resourcesSha256=(directory/'resources.sha256').read_text().strip(),captureSha256=(directory/'reference.sha256').read_text().strip(),profiles=len(rows),draws=sum(len(row['draws']) for row in rows),vertices=sum(draw['vertexCount'] for row in rows for draw in row['draws']),getterBits=GETTERS,poses=[dict(id=key[0],meta=key[1],variant=key[2],modelview=value) for key,value in sorted(poses.items())],requests=[dict(id=key[0],mode=key[1],variant=key[2],frame=key[3],**value) for key,value in sorted(requests.items())])

def verify(actual,reference,exact=False):
 expected=json.loads(Path(reference).read_text())
 for key in ['schema','profiles','draws','vertices','getterBits','requests']:
  if actual[key]!=expected[key]:raise AssertionError('Released Screen contract changed: '+key)
 if len(actual['poses'])!=len(expected['poses']):raise AssertionError('Released Screen pose coverage changed')
 for left,right in zip(actual['poses'],expected['poses']):
  if any(left[key]!=right[key] for key in ['id','meta','variant']):raise AssertionError('Released Screen pose identity changed')
  for a,b in zip(left['modelview'],right['modelview']):
   a=struct.unpack('!f',struct.pack('!I',a&0xffffffff))[0];b=struct.unpack('!f',struct.pack('!I',b&0xffffffff))[0]
   if not math.isfinite(a) or abs(a-b)>1e-6:raise AssertionError('Released Screen GL pose changed: '+left['id'])
 if exact and actual['captureSha256']!=expected['captureSha256']:raise AssertionError('Released Screen raw emission changed: '+actual['captureSha256'])

if __name__=='__main__':
 import argparse
 parser=argparse.ArgumentParser();parser.add_argument('capture',type=Path);parser.add_argument('output',type=Path);args=parser.parse_args();args.output.write_text(json.dumps(compact(args.capture),separators=(',',':'))+'\n')
