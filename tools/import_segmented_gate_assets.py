#!/usr/bin/env python3
"""Import the supplied Segmented Door artwork/models without registering new blocks."""
import argparse,hashlib,json
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
STYLES=('picket','industrial','lattice')
PARTS=('single','left','middle','right')
TILES=('raised_black_panels','ribbed_blue_steel','spiral_aluminum_glazing')
COLORS=('amber','black','dark','lattice','red','white','zinc')
IMPORTED_SHA='06ded9645985d9139f4ebeb7f5a9382e48471cf3b84b15bf5669dbd798b613f0'
PREFIX_SHA='f84739e69cac01d3a1d8e64b51b3f79414613218bb4e5b8dfefb8fa1e9b6b615'
CATALOG=ROOT/'generated-resources/assets/vandorlabs/data/unified_textures.json'
MODEL_TARGET=ROOT/'src/main/resources/assets/vandorlabs/models/block/segmented_gates'
TEXTURE_TARGET=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks'
def normalized_model(raw):return raw.replace('\r\n','\n').replace('mctrek:blocks/gates/','vandorlabs:blocks/segmented_gates/')
def files(source):
 source=(source or ROOT)/'assets/mctrek'
 for style in STYLES:
  for part in PARTS:
   name='sliding_gate_'+style+'_'+part+'.json'
   yield source/'models/block'/name,MODEL_TARGET/(style+'_'+part+'.json'),True
 for name in TILES:yield source/'textures/blocks/garage'/(name+'.png'),TEXTURE_TARGET/'segmented_doors'/(name+'.png'),False
 for name in COLORS:yield source/'textures/blocks/gates'/(name+'.png'),TEXTURE_TARGET/'segmented_gates'/(name+'.png'),False

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('source',type=Path,nargs='?');parser.add_argument('--check',action='store_true');args=parser.parse_args()
 if args.source is None and not args.check:parser.error('source pack directory is required for import')
 entries=json.loads(CATALOG.read_text());assert len(entries)>=827,'Utility artwork prerequisite missing'
 assert hashlib.sha256('\n'.join(e['id'] for e in entries[:827]).encode()).hexdigest()==PREFIX_SHA,'Existing saved catalog choices moved'
 assert [e['id'] for e in entries[815:827]]==['utility_door_'+n for n in ('blue','blue_modern','green','green_modern','warm','warm_modern','factory','industrial','industrial_modern','reactor','warehouse_detailed','warehouse_futuristic')],'Existing saved Utility choices moved'
 hashes=[];models=images=0
 for source,target,model in files(args.source):
  original=(source if args.source else target).read_bytes();data=normalized_model(original.decode('utf-8')).encode('utf-8') if model else original
  if model:
   parsed=json.loads(data);assert set(parsed)<= {'ambientocclusion','textures','elements'};assert 1<=len(parsed['elements'])<=32
   for element in parsed['elements']:
    assert len(element['from'])==len(element['to'])==3 and 1<=len(element['faces'])<=6 and set(element['faces'])<={'down','up','north','south','west','east'}
   models+=1
  else:
   with Image.open(source if args.source else target) as image:assert image.size==(128,128) and image.mode in ('RGB','RGBA'),(source.name,image.size,image.mode)
   images+=1
  if args.check:
   actual=target.read_bytes();actual=normalized_model(actual.decode('utf-8')).encode('utf-8') if model else actual
   assert actual==data,'Imported asset differs: '+str(target.relative_to(ROOT))
  else:target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
  hashes.append(str(target.relative_to(ROOT))+':'+hashlib.sha256(data).hexdigest())
 assert models==12 and images==10
 imported_hash=hashlib.sha256('\n'.join(hashes).encode()).hexdigest();assert imported_hash==IMPORTED_SHA,'Frozen supplied asset bytes changed'
 print('Segmented assets PASS:12 authored models/10 native128x128 images; namespace-only model remap; catalog827 prefix untouched sha256='+imported_hash)
if __name__=='__main__':main()
