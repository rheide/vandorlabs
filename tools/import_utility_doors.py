#!/usr/bin/env python3
"""Append native-size full-leaf Utility artwork without changing saved choices or geometry designs."""
import argparse,hashlib,json,shutil
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
KINDS=(('blue','blue_utility_door','Blue Utility'),('blue_modern','blue_utility_door_modern','Modern Blue Utility'),('green','green_utility_door','Green Utility'),('green_modern','green_utility_door_modern','Modern Green Utility'),('warm','warm_utility_door','Warm Utility'),('warm_modern','warm_utility_door_modern','Modern Warm Utility'),('factory','factory_door','Factory'),('industrial','industrial_door','Industrial'),('industrial_modern','industrial_door_modern','Modern Industrial'),('reactor','reactor_door','Reactor'),('warehouse_detailed','warehouse_door_detailed','Warehouse'),('warehouse_futuristic','warehouse_door_futuristic','Futuristic Warehouse'))
PREFIX=815
PREFIX_SHA='f11a19e1105e5518ed0c7dae4aeb866684120387b56e0a3085e28cee050f58e3'
def entry(kind,name,label):return dict(id='utility_door_'+kind,label=label,category='Doors/Utility',source='utility_doors/'+name,rectangular=True,doorArtwork=True)
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('source',type=Path,nargs='?');p.add_argument('--check',action='store_true');args=p.parse_args()
 if not args.check and args.source is None:p.error('source directory is required for import')
 target=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks/utility_doors';catalog=ROOT/'generated-resources/assets/vandorlabs/data/unified_textures.json';entries=json.loads(catalog.read_text())
 assert hashlib.sha256('\n'.join(e['id'] for e in entries[:PREFIX]).encode()).hexdigest()==PREFIX_SHA,'Existing saved choice order changed'
 updated=[dict(e,category='Doors/Glass') if e.get('design') in (15,16,29) else e for e in entries]
 assert sum(e.get('design') in (15,16,29) for e in entries)==8,'Existing Glass choices changed'
 for i,(kind,name,label) in enumerate(KINDS):
  descriptor=entry(kind,name,label);index=next((j for j,e in enumerate(updated) if e['id']==descriptor['id']),None)
  assert index is None or index==PREFIX+i,'Utility choices moved'
  if index is None:updated.append(descriptor)
  else:updated[index]=descriptor
  dest=target/(name+'.png')
  if args.source:
   source=args.source/(name+'.png')
   with Image.open(source) as image:assert image.size==(128,256) and image.mode in ('RGB','RGBA'),(name,image.size,image.mode)
   if args.check:assert source.read_bytes()==dest.read_bytes(),'Supplied artwork changed: '+name
   else:target.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,dest)
  with Image.open(dest) as image:assert image.size==(128,256),name
 if args.check:assert entries==updated,'Shared catalog differs from Utility/Glass policy'
 else:catalog.write_text(json.dumps(updated,indent=2)+'\n')
 print('Utility Door artwork PASS: 12 native128x256 sheets, original815 IDs/order, eight existing Glass choices, geometry designs unchanged')
if __name__=='__main__':main()
