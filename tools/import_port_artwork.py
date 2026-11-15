#!/usr/bin/env python3
"""Import port artwork at the established block resolution and append its Hull face."""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageFilter
import colorsys

ROOT=Path(__file__).resolve().parents[1]
CATALOG=ROOT/'generated-resources/assets/vandorlabs/data/unified_textures.json'
TARGET=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks/resource_ports'
NAMES=['port-hull']+[kind+'-'+direction+'-'+state for kind in ('power','fluid','matter') for direction in ('in','out') for state in ('off','on')]
PREFIX_COUNT=850
PREFIX_SHA='3eb656e2f4d2dd01c98f5be1f2c23d1a27642336bcbbe895177287dd23e53768'
ICON_HUES={'power':0.0,'fluid':0.60,'matter':0.14}

def normalized(original):
    return original.convert('RGBA').resize((64,64),Image.Resampling.LANCZOS).filter(ImageFilter.UnsharpMask(radius=0.65,percent=85,threshold=3))

def icon_art(art,kind):
    result=art.copy();pixels=result.load()
    for y in range(result.height):
        for x in range(result.width):
            r,g,b,a=pixels[x,y];h,s,v=colorsys.rgb_to_hsv(r/255,g/255,b/255)
            if s>.55 and v>.45 and (.43<h<.57 or .025<h<.085):
                red,green,blue=colorsys.hsv_to_rgb(ICON_HUES[kind],s,v)
                pixels[x,y]=round(red*255),round(green*255),round(blue*255),a
    return result

ENTRY=dict(id='port_hull',label='Port Hull',category='Hull',source='resource_ports/port_hull')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source',type=Path,nargs='?')
    parser.add_argument('--check',action='store_true')
    parser.add_argument('--assets-only',action='store_true',help='Do not rewrite the shared selectable-texture catalog')
    args=parser.parse_args()
    if not args.check and args.source is None:parser.error('source artwork directory required')
    entries=json.loads(CATALOG.read_text())
    assert hashlib.sha256('\n'.join(e['id'] for e in entries[:PREFIX_COUNT]).encode()).hexdigest()==PREFIX_SHA,'Earlier saved texture choices moved'
    if len(entries)==PREFIX_COUNT:entries.append(ENTRY)
    assert entries[PREFIX_COUNT]==ENTRY,'Port Hull saved choice moved'
    for name in NAMES:
        target=TARGET/(name.replace('-','_')+'.png')
        if args.source:
            with Image.open(args.source/(name+'.png')) as original:
                art=normalized(original)
                if args.check:
                    with Image.open(target) as actual:assert actual.size==(64,64) and actual.convert('RGBA').tobytes()==art.tobytes(),name
                else:
                    target.parent.mkdir(parents=True,exist_ok=True);art.save(target,optimize=True)
        with Image.open(target) as actual:assert actual.size==(64,64),name
    for kind in ('power','fluid','matter'):
        for direction in ('in','out'):
            with Image.open(TARGET/(kind+'_'+direction+'_off.png')) as art:
                icon=icon_art(art.convert('RGBA'),kind)
            target=TARGET/(kind+'_'+direction+'_icon.png')
            if args.check:
                with Image.open(target) as actual:assert actual.size==(64,64) and actual.convert('RGBA').tobytes()==icon.tobytes(),target.name
            else:icon.save(target,optimize=True)
    if args.check:assert json.loads(CATALOG.read_text())==entries,'Missing Hull entry'
    elif not args.assets_only:CATALOG.write_text(json.dumps(entries,indent=2)+'\n')
    print('Port artwork PASS: thirteen sharpened64x64 faces +six color-coded item faces; original850 saved texture IDs/order retained; Port Hull appended')

if __name__=='__main__':main()
