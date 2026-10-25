#!/usr/bin/env python3
"""Inspect actual immediate trigger settings and four server-driven artwork bands."""
import argparse
import json
from pathlib import Path
import numpy as np
from PIL import Image
from test_modern_screen_images import glyph_overlap


def need(value,message):
    if not value:raise AssertionError(message)


def gui(image,scale,bands=False):
    image=image.convert('RGB');need(image.size==(1280,720),'actual1280x720 image required');width,height=(1280+scale-1)//scale,(720+scale-1)//scale;pw,ph=min(460,width-8),min(332,height-8);left,top=(width-pw)//2,(height-ph)//2;pixels=np.asarray(image)
    def sample(x,y):return tuple(pixels[y*scale+scale//2,x*scale+scale//2])
    def crop(x,y,w,h):return image.crop((x*scale,y*scale,(x+w)*scale,(y+h)*scale)).resize((w,h),Image.Resampling.NEAREST)
    need(sample(left+1,top+1)==(32,32,40),'trigger header/scale differs');need(sample(left+1,top+25)==(16,16,18),'trigger body palette differs');controls=left+pw-166;listx=left+12;listw=controls-listx-19;listy=top+54
    need(sample(listx-1,listy-1)==(0,0,0),'material list border differs');need(sample(controls+1,top+43)==(0,0,0),'channel field bounds differ')
    title=crop(left+12,top+8,190,9);need(np.all(np.asarray(title)>180,axis=2).sum()>180,'trigger title missing');count=4 if bands else 2
    for i in range(count):
        width=(listw+2)//count;label=crop(listx+i*width+2,top+34,width-6,10);a=np.asarray(label);need(((a[:,:,0]>150)&(a[:,:,1]>150)&(a[:,:,2]>140)).sum()>25,'visible trigger tab glyphs missing')
    selected=[]
    for row in range(max(2,(ph-60)//22)):
        strip=pixels[(listy+row*22+1)*scale,listx*scale:(listx+listw)*scale]
        if np.mean(np.all(strip==(42,74,106),axis=1))>.95:selected.append(row)
    need(len(selected)==1,'exactlyone selected finish required');thumb=crop(listx+3,listy+selected[0]*22+3,16,16);need(len(np.unique(np.asarray(thumb).reshape(-1,3),axis=0))>5,'selected finish thumbnail is blank')
    return {'geometry':[left,top,pw,ph],'tabs':count},title


def artwork(a,b):
    # Dark authored finishes have subtle RGB differences; require a broad changed area.
    count=int(np.any(np.abs(a-b)>3,axis=2).sum());need(count>2000,f"actual band artwork didnot change: {count}");return count


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--screenshots',type=Path,required=True);parser.add_argument('--output-dir',type=Path,required=True);parser.add_argument('--legacy',type=Path);args=parser.parse_args()
    images={name:Image.open(args.screenshots/f'modern-trigger-{name}.png').convert('RGB') for name in ('gui3','gui2','bands-exact','band-0','band-3','band-7','band-13')}
    a,at=gui(images['gui3'],3);b,bt=gui(images['gui2'],2);c,_=gui(images['bands-exact'],2,True);mask=lambda im:np.all(np.asarray(im)>180,axis=2);score=glyph_overlap(mask(at),mask(bt));need(score>=.9,'trigger title glyphs change between GUI scales')
    if args.legacy:
        _,old=gui(Image.open(args.legacy),3);need(glyph_overlap(mask(at),mask(old))>=.9,'canonical trigger title differs fromlegacy')
    changes=[];crops=[np.asarray(images[f'band-{level}'].crop((540,260,740,460)),dtype=int) for level in (0,3,7,13)]
    for i in range(4):
        for j in range(i):
            changes.append(artwork(crops[i],crops[j]))
    try:artwork(crops[0],crops[0])
    except AssertionError:pass
    else:raise AssertionError("unchanged artwork negative accepted")
    for image,scale,label in ((images['gui2'],3,'wrongscale'),(images['band-0'],3,'worldasGUI')):
        try:gui(image,scale)
        except AssertionError:pass
        else:raise AssertionError('negativeaccepted '+label)
    args.output_dir.mkdir(parents=True,exist_ok=True);(args.output_dir/'trigger-pixel-contracts.json').write_text(json.dumps({'status':'PASS','gui3':a,'gui2':b,'bands':c,'title_overlap':score,'world_changed_pixels':changes,'negative_controls':3},indent=2)+'\n');print('Trigger pixels PASS: exact GUI palettes/bounds/title, two/four tabs, authored thumbnails andsix pairwise actualworld band changes;3negativecontrols rejected')


if __name__=='__main__':main()
