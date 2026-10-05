#!/usr/bin/env python3
"""Native dimensions, transparent cutouts, whole-bay UV seams and inventory padding."""
import json, math, hashlib
from pathlib import Path
from PIL import Image
from import_cargo_doors import FAMILIES
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'generated-resources/assets/vandorlabs'
load=lambda p:json.loads(p.read_text())
current=load(ASSETS/'data/unified_textures.json')
assert hashlib.sha256(json.dumps(current[:254],sort_keys=True).encode()).hexdigest()=='e6348fcfa58a74e38276ad8167bec4a7caf57de6d03cc504bff66a907b2b8ed6','Saved texture IDs changed'
count=0
for tier,height in [('low',256),('medium',512),('high',1024)]:
 for index,family in enumerate(FAMILIES):
  image=Image.open(ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks/cargo_doors'/tier/(family+'.png'))
  assert image.size==(height//2 if index<4 else height,height)
  alpha=set(image.convert('RGBA').getchannel('A').getdata());assert alpha<= {0,255}
  assert (0 in alpha)==(index in (2,5,7,9,11))
  for sliding in [False,True]:
   for framed in [False,True]:
    base='space_'+family+('_sliding_' if sliding else '_rotating_')+('framed' if framed else 'bare')
    left=load(ASSETS/'models/block/detailed_doors'/tier/(base+'_paired_left_leaf.json'))
    right=load(ASSETS/'models/block/detailed_doors'/tier/(base+'_paired_right_leaf.json'))
    for a,b in zip(left['elements'][:2],right['elements'][:2]):
     au=a['faces']['south']['uv'];bu=b['faces']['south']['uv']
     if index>=4:
      assert au[2]==bu[0]==8,'Full-bay artwork does not meet at the center'
      assert au[0]<au[2] and bu[0]<bu[2]
     else:assert bu==[au[2],au[1],au[0],au[3]],'Left artwork was not mirrored'
    for suffix in ([''] if sliding else ['','_no_hinges']):
     item=load(ASSETS/'models/item/detailed_doors'/tier/('large_'+base+'_left_leaf'+suffix+'.json'))
     assert item['display']['gui']['scale']==[.65,.65,.65]
     # GUI X projection after its Y rotation; account for model depth, not just width.
     for e in item['elements']:
      for bound in ('from','to'):
       x,y,z=[value-8 for value in e[bound]]
       projected=.65*(math.cos(math.radians(205))*x+math.sin(math.radians(205))*z)
       assert abs(projected)<7,'Hotbar icon lost its horizontal padding'
     count+=1
print('PASS: 36 native PNGs, alpha holes, stable catalog prefix, split/mirrored UVs and',count,'padded cargo icons')
