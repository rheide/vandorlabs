#!/usr/bin/env python3
"""Compare actual joined Stretch/Fit and repeated Tile artwork in the native world."""
import argparse,json
from pathlib import Path
import numpy as np
from PIL import Image

def measure(path):
    image=Image.open(path).convert('RGB')
    if image.size!=(1280,720):raise AssertionError('Expected actual 1280x720 world capture')
    rgb=np.asarray(image,dtype=int)[180:550,520:760];r,g,b=rgb[:,:,0],rgb[:,:,1],rgb[:,:,2]
    mask=(r<140)&(g>r+20)&(b>r+20)&(np.maximum(g,b)>65)
    seen=np.zeros(mask.shape,dtype=bool);parts=[]
    for y,x in zip(*np.where(mask)):
        if seen[y,x]:continue
        stack=[(y,x)];seen[y,x]=True;xs=[];ys=[]
        while stack:
            yy,xx=stack.pop();xs.append(xx);ys.append(yy)
            for dy,dx in ((0,1),(0,-1),(1,0),(-1,0)):
                ny,nx=yy+dy,xx+dx
                if 0<=ny<mask.shape[0] and 0<=nx<mask.shape[1] and mask[ny,nx] and not seen[ny,nx]:seen[ny,nx]=True;stack.append((ny,nx))
        if len(xs)>5:parts.append({'area':len(xs),'width':int(max(xs)-min(xs)+1),'height':int(max(ys)-min(ys)+1),'x':float((min(xs)+max(xs))/2),'y':float((min(ys)+max(ys))/2)})
    if not parts:raise AssertionError('Authored cyan company artwork missing from rendered plane')
    maximum=max(p['height'] for p in parts);major=[p for p in parts if p['height']>=maximum*.65 and p['area']>=max(q['area'] for q in parts)*.15]
    return major

def validate(stretch,tile,fit):
    if len(stretch)!=1 or len(fit)!=1 or len(tile)!=6:raise AssertionError(f'Expected one Stretch/Fit logo and six Tile logos; got {len(stretch)}/{len(fit)}/{len(tile)}')
    ratio=stretch[0]['height']/fit[0]['height'];width=stretch[0]['width']/fit[0]['width']
    if not 1.35<ratio<1.65 or not .9<width<1.1:raise AssertionError(f'Fit failed to retain square image aspect against 2x3 Stretch: height {ratio}, width {width}')
    return {'stretch_to_fit_height':ratio,'stretch_to_fit_width':width}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--screenshots',type=Path,required=True);parser.add_argument('--output-dir',type=Path,required=True);args=parser.parse_args()
    parts=[measure(args.screenshots/f'modern-joined-{mode}.png') for mode in range(3)]
    ratios=validate(*parts);negative=0
    for values in ((parts[2],parts[1],parts[0]),(parts[0],parts[0],parts[2])):
        try:validate(*values)
        except AssertionError:negative+=1
    if negative!=2:raise AssertionError('Joined image checker did not reject swapped/repeated screenshots')
    args.output_dir.mkdir(parents=True,exist_ok=True);(args.output_dir/'joined-pixel-contracts.json').write_text(json.dumps({'status':'PASS','planes':parts,'ratios':ratios,'negative_controls':negative},indent=2)+'\n')
    print('Joined pixels PASS: one continuous Stretch/Fit logo, six independent Tile logos, preserved Fit aspect and two substituted-image rejections')
if __name__=='__main__':main()
