#!/usr/bin/env python3
"""Inspect installed power-lever/throttle dialogs, changing artwork and configured icons."""
import argparse
import hashlib
import json
from pathlib import Path
import numpy as np
from PIL import Image
from test_modern_control_images import crop, glyph, need, reject
from test_modern_screen_images import glyph_overlap

IDS = ('industrial_power_lever','compact_power_lever','small_power_lever','large_power_lever',
       'thruster_lever','airliner_throttle','fighter_throttle','wall_slider')


def dialog(image, scale, family):
    image = image.convert('RGB')
    need(image.size == (1280,720), 'actual1280x720 capture required')
    signal, selectable = family >= 4, 4 <= family <= 6
    mount = (146 if selectable else 122) if signal else 96
    height = mount + 102
    width, screen_height = (1280+scale-1)//scale, (720+scale-1)//scale
    left, top = (width-240)//2, (screen_height-height)//2
    pixels = np.asarray(image)
    def sample(x,y):return tuple(pixels[y*scale+scale//2,x*scale+scale//2])
    need(sample(left+1,top+18) == (48,72,88), 'control header palette/scale differs')
    need(sample(left+1,top+25) == (16,16,18), 'control body palette/bounds differ')
    need(sample(left+117,top+39) == (0,0,0), 'channel field differs from authored bounds')
    title = crop(image,scale,left+12,top+8,160,9)
    need(glyph(title).sum()>140, 'Redstone Channels title missing')
    controls = [(14,mount,212),(14,mount+24,102),(124,mount+24,102),(20,mount+74,200)]
    controls += [(14,66,102),(124,66,102)] if signal else [(14,66,212)]
    if selectable:controls.append((14,122,212))
    labels=[]
    for x,y,w in controls:
        label=crop(image,scale,left+x+3,top+y+5,w-6,10)
        need(glyph(label).sum()>30, 'control option/Done label missing')
        labels.append(label)
    return title,labels,[left,top,240,height]


def icon_page(image, count):
    image=image.convert('RGB')
    pixels=np.asarray(image)
    need(tuple(pixels[0,0])==(16,16,18), 'icons were blurred/tinted after rendering')
    left,top=(640-352)//2,(360-176)//2
    bounds=[];unique=set()
    for index in range(count):
        x,y=left+index%16*22+2,top+index//16*22+2
        # Inspect every framebuffer pixel: nearest-neighbor shrinking can erase a thin authored handle.
        cell=np.asarray(image.crop((x*2,y*2,(x+16)*2,(y+16)*2)),dtype=int)
        mask=np.any(np.abs(cell-(40,44,50))>3,axis=2)
        ys,xs=np.where(mask)
        need(len(xs)>=8, f'configured item{index} empty')
        box=[int(xs.min()),int(ys.min()),int(xs.max()+1),int(ys.max()+1)]
        need(box[0]>=2 and box[1]>=2 and box[2]<=30 and box[3]<=30,
             f'configured item{index} lacks padding: {box}')
        bounds.append(box);unique.add(hashlib.sha256(cell.tobytes()).hexdigest())
    need(len(unique)>=8, 'configured item mount/size models ignored')
    return {'count':count,'framebuffer_pixels_per_logical_pixel':2,'bounds':bounds,'distinct_models':len(unique)}


def artwork(off,on):
    a=np.asarray(off.convert('RGB').crop((520,240,760,480)),dtype=int)
    b=np.asarray(on.convert('RGB').crop((520,240,760,480)),dtype=int)
    changed=int(np.any(np.abs(a-b)>8,axis=2).sum())
    need(changed>=12, f'actual control artwork unchanged ({changed})')
    return changed


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--screenshots',type=Path,required=True)
    parser.add_argument('--output-dir',type=Path,required=True)
    args=parser.parse_args();result={};negative=0
    for family,id in enumerate(IDS):
        images={suffix:Image.open(args.screenshots/f'modern-control-{id}-{suffix}.png').convert('RGB')
                for suffix in ('world','active','gui3','gui2','edited','configured')}
        a,b=dialog(images['gui3'],3,family),dialog(images['gui2'],2,family)
        scores=[]
        for left,right in zip([a[0]]+a[1],[b[0]]+b[1]):
            score=glyph_overlap(glyph(left),glyph(right))
            need(score>=.9, f'{id} title/option glyph changes across scales ({score})')
            scores.append(score)
        dialog(images['edited'],2,family)
        icons=Image.open(args.screenshots/f'modern-expanded-control-icons-{family}.png')
        count=128 if family<4 else 64
        result[id]={'gui3':a[2],'gui2':b[2],'glyph_overlap':scores,
                    'active_changed_pixels':artwork(images['world'],images['active']),
                    'icons':icon_page(icons,count)}
        reject(lambda:dialog(images['gui2'],3,family), id+' wrongscale')
        reject(lambda:dialog(images['world'],3,family), id+' worldasGUI')
        reject(lambda:artwork(images['world'],images['world']), id+' unchanged artwork')
        negative+=3
    reject(lambda:icon_page(Image.new('RGB',(1280,720),(40,44,50)),64),'blank icons');negative+=1
    args.output_dir.mkdir(parents=True,exist_ok=True)
    (args.output_dir/'expanded-control-pixel-contracts.json').write_text(json.dumps(
        {'status':'PASS','families':result,'rendered_icons':768,'negative_controls':negative},indent=2)+'\n')
    print('Expanded control pixels PASS: eight actual dialogs atGUI3/2, reverse-edited labels, changing world artwork and768 padded configured icons;25negativecontrols rejected')


if __name__=='__main__':main()
