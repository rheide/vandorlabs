#!/usr/bin/env python3
"""Generate padded Data Chip models from the four supplied chip faces without changing company IDs."""
import argparse,json
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-dir',type=Path)
args=parser.parse_args()
models=ROOT/'src/main/resources/assets/vandorlabs/models/item'
textures=ROOT/'texture-packs/additional/assets/vandorlabs/textures/items'
base=json.loads((models/'data_chip_unbranded.json').read_text())
base.pop('overrides',None)
types=('journal','vehicle','navigation','catalog')
for kind in types:
    if args.source_dir:
        image=Image.open(args.source_dir/(kind+'.png')).convert('RGBA')
        width,height=image.size
        scale=min(64/width,64/height)
        w,h=max(1,round(width*scale)),max(1,round(height*scale))
        pixels=image.load();scaled=Image.new("RGBA",(w,h))
        scaled.putdata([pixels[min(width-1,int((x+.5)*width/w)),min(height-1,int((y+.5)*height/h))] for y in range(h) for x in range(w)])
        image=scaled
        padded=Image.new('RGBA',(64,64));padded.alpha_composite(image,((64-image.width)//2,(64-image.height)//2))
        padded.save(textures/('data_chip_'+kind+'.png'))
    model=json.loads(json.dumps(base))
    for key in ('front','particle'):model['textures'][key]='vandorlabs:items/data_chip_'+kind
    (models/('data_chip_'+kind+'.json')).write_text(json.dumps(model,separators=(',',':'))+'\n')
for company in ('unbranded','pinnacle','ashiro','paladyne','vektor','seyr','vandor'):
    path=models/('data_chip_'+company+'.json');model=json.loads(path.read_text())
    model['overrides']=[{'predicate':{'vandorlabs:chip_type':index},'model':'vandorlabs:item/data_chip_'+kind} for index,kind in enumerate(types,1)]
    path.write_text(json.dumps(model,separators=(',',':'))+'\n')
