#!/usr/bin/env python3
"""Check construction counts/stage links and, after capture, exact asserted source images."""
import argparse,json,hashlib
from pathlib import Path
from PIL import Image
from generate_machine_build_guides import NEW_GUIDES,frame
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--allow-missing-images',action='store_true');p.add_argument('--capture',type=Path);args=p.parse_args()
specs={s['design']:s for s in json.loads((ROOT/'src/main/resources/assets/vandorlabs/data/frame_machines.json').read_text()) if s['design'] in NEW_GUIDES}
records={}
if args.capture:
 for row in json.loads((args.capture/'machine-build-steps.json').read_text()):
  if row['design'] in specs:
   key=(row['design'],row['step']);assert key not in records;records[key]=row
 assert set(records)=={(design,step) for design in specs for step in range(1,6)},'Capture must include all25 new Core stages'
missing=[]
for design,spec in specs.items():
 folder=ROOT/'docs/multiblocks'/design.replace('_','-');text=(folder/'README.md').read_text();w,h,d=spec['width'],spec['height'],spec['depth'];matter=sum(frame(spec,x,y,z) for y in range(h) for x in range(w) for z in range(d))-1
 assert f'{w} wide × {d} deep × {h} high' in text and f'{matter} Programmable Matter Blocks' in text,design
 for step in range(1,6):
  assert f'## {step}. ' in text and f'](step-{step}.png)' in text,(design,step)
  image=folder/f'step-{step}.png'
  if not image.exists():missing.append(str(image.relative_to(ROOT)));continue
  with Image.open(image) as actual:assert actual.width>=320 and actual.height>=240 and len(actual.convert('RGB').getcolors(1024) or [None]*1025)>8,'Construction image is empty or too small'
  if args.capture:
   row=records[design,step];assert row['asserted'] and all(row.get(k)==v for k,v in dict(width=w,depth=d,height=h,matter=matter,core=spec['block']).items()),'Capture construction metadata differs from catalog'
   assert hashlib.sha256(image.read_bytes()).digest()==hashlib.sha256((args.capture/row['image']).read_bytes()).digest(),'Published stage differs from asserted in-game capture'
 if w==2 and d==2:assert 'no hollow interior or open roof center' in text,'Compact 2×2 frame must not request an empty interior'
assert args.allow_missing_images or not missing,'Missing actual construction stages: '+', '.join(missing)
print(f'Core build guides PASS five designs25stages dimensions/material counts and useful2×2layers; images present={25-len(missing)}'+('; pending actual capture' if missing else ''))
