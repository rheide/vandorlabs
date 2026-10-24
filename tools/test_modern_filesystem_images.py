#!/usr/bin/env python3
"""Inspect an existing picker's authored thumbnail shape through filesystem reloads."""
import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image


def thumbnail(path,screen=False):
    image=Image.open(path).convert('RGB')
    if image.size!=(1280,720):raise AssertionError('Expected GUI-scale-2 1280x720 filesystem capture')
    scale=2;width,height=640,360;pw,ph=min(460,width-8),min(332,height-8)
    left,top=(width-pw)//2,(height-ph)//2;list_x=left+12;controls=left+pw-166;list_width=controls-list_x-19;list_y=top+(54 if screen else 30)
    data=np.asarray(image)
    if tuple(data[(top+1)*scale,(left+1)*scale])!=((48,72,88) if screen else (32,32,40)):raise AssertionError('Filesystem capture does not show the actual picker header')
    if tuple(data[(top+(25 if screen else 20))*scale,(left+1)*scale])!=((25,35,44) if screen else (16,16,18)):raise AssertionError('Filesystem capture does not show the actual picker panel')
    selected=[]
    for row in range((ph-64)//22):
        strip=data[(list_y+row*22+1)*scale,list_x*scale:(list_x+list_width)*scale]
        if np.mean(np.all(strip==(42,74,106),axis=1))>.95:selected.append(row)
    if len(selected)!=1:raise AssertionError(f'Expected one retained selected filesystem row, got {selected}')
    y=list_y+selected[0]*22+3
    crop=image.crop(((list_x+3)*scale,y*scale,(list_x+19)*scale,(y+16)*scale)).resize((16,16),Image.Resampling.NEAREST)
    return crop


def authored_bounds(image):
    data=np.asarray(image,dtype=int);colors=np.asarray([(224,76,50),(50,89,224),(50,200,116),(224,184,50)])
    mask=np.any(np.max(np.abs(data[:,:,None,:]-colors[None,None,:,:]),axis=3)<=10,axis=2)
    ys,xs=np.where(mask)
    if not len(xs):raise AssertionError('Authored filesystem pixels missing from thumbnail')
    return [int(xs.min()),int(ys.min()),int(xs.max()+1),int(ys.max()+1)]


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--screenshots',type=Path,required=True)
    parser.add_argument('--output-dir',type=Path,required=True)
    parser.add_argument('--screen',action='store_true',help='Inspect the existing programmable-screen picker')
    args=parser.parse_args();prefix='modern-screen-filesystem' if args.screen else 'modern-filesystem';crops={name:thumbnail(args.screenshots/f'{prefix}-picker-{name}.png',args.screen) for name in ('portrait','missing','wide')}
    portrait,wide=authored_bounds(crops['portrait']),authored_bounds(crops['wide'])
    if portrait!=[4,0,12,16]:raise AssertionError(f'Portrait thumbnail aspect/centering differs: {portrait}')
    if wide!=[0,6,16,10]:raise AssertionError(f'Already-open picker retained stale dimensions: {wide}')
    missing=np.asarray(crops['missing']);fraction=float(np.mean(np.all(missing==(50,57,68),axis=2)))
    if fraction<.98:raise AssertionError(f'Missing file fallback differs from legacy gray16 sprite: {fraction:.3f}')
    args.output_dir.mkdir(parents=True,exist_ok=True)
    for name,crop in crops.items():crop.save(args.output_dir/f'filesystem-{name}-thumbnail.png')
    result={'status':'PASS','portrait_bounds':portrait,'wide_bounds':wide,'legacy_fallback_fraction':fraction}
    (args.output_dir/'filesystem-pixel-contracts.json').write_text(json.dumps(result,indent=2)+'\n')
    print('Filesystem pixels PASS: portrait-to-wide aspect updates in the existing picker, authored pixels and exact legacy missing-file fallback')


if __name__=='__main__':main()
