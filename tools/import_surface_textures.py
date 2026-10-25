#!/usr/bin/env python3
"""Import surface masters at the established 512px pad resolution and 64px local detail."""
import argparse,json
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
NAMES=('landing','landing-grey','maintenance','maintenance-grey','docking','docking-grey','copier-input','copier-output')
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('source',type=Path);args=p.parse_args()
 out=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks/surface';out.mkdir(exist_ok=True)
 catalog=ROOT/'texture-packs/additional/turntable-catalog.json';entries=json.loads(catalog.read_text())
 for entry in entries:entry['category']='Surfaces'
 entries=[entry for entry in entries if not entry['id'].startswith('surface_')]
 for name in NAMES:
  with Image.open(args.source/(name+'.png')) as image:
   image=image.convert('RGBA');image.resize((512,512),Image.Resampling.LANCZOS).save(out/(name+'.png'),optimize=True);image.resize((64,64),Image.Resampling.LANCZOS).save(out/(name+'_low.png'),optimize=True)
  entries.append(dict(id='surface_'+name.replace('-','_'),label=name.replace('-',' ').title(),category='Surfaces',source='surface/'+name))
 catalog.write_text(json.dumps(entries,indent=2)+'\n')
 print('Imported eight Surface masters at512px with64px local variants')
if __name__=='__main__':main()
