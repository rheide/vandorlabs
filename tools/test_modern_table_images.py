#!/usr/bin/env python3
"""Table world/item pixel evidence and cross-version actual baked-quad parity."""
import argparse
import copy
import json
from pathlib import Path
import numpy as np
from PIL import Image

def require(value,message):
    if not value:raise AssertionError(message)

def compare_quads(actual,expected):
    require(len(actual)==len(expected),'Baked face counts differ')
    remaining=list(expected);max_uv=0
    for quad in actual:
        av=np.array(quad['vertices'],dtype=float);found=None
        for i,other in enumerate(remaining):
            ev=np.array(other['vertices'],dtype=float);distances=np.max(np.abs(av[:,None,:3]-ev[None,:,:3]),axis=2);order=distances.argmin(axis=1)
            if quad['sprite']==other['sprite'] and len(set(order))==4 and np.max(distances[np.arange(4),order])<.00002:
                found=(i,ev[order]);break
        require(found is not None,'Native baked position/material has no legacy counterpart')
        i,ev=found;remaining.pop(i);error=float(np.max(np.abs(av[:,3:]-ev[:,3:])));max_uv=max(max_uv,error)
        require(error<.002,'Native authored UV differs from legacy: '+str(error))
    return max_uv

def compare_collision(actual,expected):
    a=np.array(actual);b=np.array(expected);axes=[np.unique(np.round(np.concatenate((a[:,axis],a[:,axis+3],b[:,axis],b[:,axis+3])),8)) for axis in range(3)]
    centers=[(values[:-1]+values[1:])/2 for values in axes];points=np.stack(np.meshgrid(*centers,indexing='ij'),axis=-1).reshape(-1,3)
    def inside(boxes):return np.any(np.all((points[:,None,:]>boxes[None,:,:3]-1e-10)&(points[:,None,:]<boxes[None,:,3:]+1e-10),axis=2),axis=1)
    require(np.array_equal(inside(a),inside(b)),'Native collision union differs from actual legacy collision')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--screenshots',type=Path,required=True);parser.add_argument('--legacy-models',type=Path);parser.add_argument('--report',type=Path,required=True);args=parser.parse_args()
    models=json.loads((args.screenshots/'industrial-table-native-models.json').read_text());require(len(models['states'])==32 and len(models['boundaries'])==8,'Incomplete native geometry coverage')
    changes=[]
    for i in range(32):
        positive=np.array(Image.open(args.screenshots/f'modern-table-state-{i}.png').convert('RGB'),dtype=int);negative=np.array(Image.open(args.screenshots/f'modern-table-state-{i}-empty.png').convert('RGB'),dtype=int)
        require(positive.shape==negative.shape,'World negative-control viewport changed');height,width=positive.shape[:2]
        mask=np.max(np.abs(positive-negative),axis=2)>12;box=(slice(height//2-155,height//2+155),slice(width//2-230,width//2+230));count=int(mask[box].sum());require(count>150,'Table world image lacks visible geometry: '+str(i))
        def cyan(image):r,g,b=image[:,:,0],image[:,:,1],image[:,:,2];return (r<80)&(g>55)&(b>70)&(g>r*1.4)&(b>r*1.5)
        # A selection outline or a delayed terrain frame must not pass as rendered authored artwork.
        authored=cyan(positive)&~cyan(negative);require(int(authored[box].sum())>25,'Missing actual cyan tabletop artwork against empty control: '+str(i));changes.append(count)
    icons=np.array(Image.open(args.screenshots/'modern-table-items.png').convert('RGB'),dtype=int);h,w=icons.shape[:2];scale=2;left=(w//scale-176)//2;top=(h//scale-88)//2;background=np.array([40,44,50]);insets=[];reference=None
    for i in range(32):
        x=(left+i%8*22)*scale;y=(top+i//8*22)*scale;cell=icons[y:y+20*scale,x:x+20*scale];mask=np.max(np.abs(cell-background),axis=2)>8;yy,xx=np.where(mask);require(len(xx)>10,'Missing table item icon')
        inset=min(xx.min()/scale-2,yy.min()/scale-2,17-xx.max()/scale,17-yy.max()/scale);require(inset>=.5,'Table icon touches inventory slot edge: '+str(inset));insets.append(float(inset))
        if reference is None:reference=cell
        else:require(np.array_equal(reference,cell),'Plain items from different table states differ')
    max_uv=None
    if args.legacy_models:
        legacy=json.loads(args.legacy_models.read_text());require(len(legacy['states'])==32,'Incomplete actual legacy model coverage')
        max_uv=0
        for a,b in zip(models['states'],legacy['states']):max_uv=max(max_uv,compare_quads(a['quads'],b['quads']));compare_collision(a['collision'],b['collision'])
        max_uv=max(max_uv,compare_quads(models['item'],legacy['item']))
        bad=copy.deepcopy(models['states'][0]['quads']);bad[0]['vertices'][0][3]+=.25
        try:compare_quads(bad,legacy['states'][0]['quads'])
        except AssertionError:pass
        else:raise AssertionError('Deliberately wrong authored UV negative control accepted')
    result={'status':'PASS','worldStates':32,'worldChangedPixels':changes,'chunkBoundaryCases':8,'plainItemStates':32,'minItemLogicalPadding':min(insets),'maxLegacyUvError':max_uv}
    args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(result,indent=2)+'\n');print('Table pixel/model contracts PASS:',json.dumps(result))

if __name__=='__main__':main()
