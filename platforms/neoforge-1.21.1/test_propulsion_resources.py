#!/usr/bin/env python3
"""Check the inactive authored projection against released assets, without a game launch."""
import hashlib,json
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
ASSETS=Path(__file__).resolve().parent/'build/generated/propulsion-resources/assets/vandorlabs'
def main():
 data=json.loads((ASSETS/'data/propulsion_artwork.json').read_text());assert len(data['blocks'])==27 and len(data['models'])==2871 and len(data['textures'])==24
 models=faces=0
 for name,descriptor in data['models'].items():
  namespace,path=name.split(':',1);target=ASSETS/'models'/(path+'.json');projected=json.loads(target.read_text());source=descriptor['source'].split(':',1)[1];source=('block/'+source) if not source.startswith(('block/','item/')) else source
  original=ROOT/'generated-resources/assets/vandorlabs/models'/source
  if descriptor['kind']=='obj':
   assert projected['automatic_culling'] is False and projected['emissive_ambient'] is False and projected['flip_v'] is True
   raw=(ASSETS/'models'/(path+'.obj')).read_bytes();assert hashlib.sha256(raw).hexdigest()==descriptor['sha256']==hashlib.sha256(original.read_bytes()).hexdigest()
   for line in raw.decode().splitlines():
    if line.startswith('mtllib '):
     material=line.split(None,1)[1];assert (target.parent/material).read_bytes()==(original.parent/material).read_bytes()
   faces+=len(descriptor['geometry'])
  else:
   original=original.with_suffix('.json');value=json.loads(original.read_text());assert hashlib.sha256(original.read_bytes()).hexdigest()==descriptor['sha256']
   assert projected.get('elements')==value.get('elements') and projected.get('display')==value.get('display')
  models+=1
 for name,descriptor in data['textures'].items():
  path=name.split(':',1)[1];projected=ASSETS/'textures'/(path+'.png');original=ROOT/'texture-packs/default/assets/vandorlabs/textures'/(path+'.png');assert projected.read_bytes()==original.read_bytes();assert hashlib.sha256(projected.read_bytes()).hexdigest()==descriptor['sha256'];assert Image.open(projected).size==(descriptor['width'],descriptor['height'])
 variants=sum(len(v) for v in data['blocks'].values());assert variants==17604 and faces==6812
 print('Native propulsion resource projection PASS models=%d variants=%d OBJfaces=%d sprites=24 exactSource geometry/MTL/standard128px; no generic housing replacement'%(models,variants,faces))
if __name__=='__main__':main()
