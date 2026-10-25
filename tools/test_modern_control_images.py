#!/usr/bin/env python3
"""Inspect actual mounted-control dialogs, pulse artwork and every configured item icon."""
import argparse
import hashlib
import json
from pathlib import Path
import numpy as np
from PIL import Image
from test_modern_screen_images import glyph_overlap


def need(value,message):
    if not value:raise AssertionError(message)


def crop(image,scale,x,y,w,h):
    return image.crop((x*scale,y*scale,(x+w)*scale,(y+h)*scale)).resize((w,h),Image.Resampling.NEAREST)


def glyph(image):
    a=np.asarray(image);return (a[:,:,0]>180)&(a[:,:,1]>180)&(a[:,:,2]>140)


def dialog(image,scale):
    image=image.convert('RGB');need(image.size==(1280,720),'actual1280x720 capture required')
    width,height=(1280+scale-1)//scale,(720+scale-1)//scale;left,top=(width-240)//2,(height-168)//2;a=np.asarray(image)
    def sample(x,y):return tuple(a[y*scale+scale//2,x*scale+scale//2])
    need(sample(left+1,top+1)==(48,72,88),'header palette/scale differs')
    need(sample(left+1,top+25)==(16,16,18),'body palette/bounds differ')
    need(sample(left+117,top+39)==(0,0,0),'channel field does not match legacy bounds')
    title=crop(image,scale,left+12,top+8,160,9)
    need(glyph(title).sum()>140,'Redstone Channels title missing')
    labels=[]
    for x,y,w in [(14,66,212),(14,90,102),(124,90,102),(20,140,200)]:
        label=crop(image,scale,left+x+3,top+y+5,w-6,10);need(glyph(label).sum()>35,'mount/Done label missing');labels.append(label)
    return title,labels,[left,top,240,168]


def icons(image):
    a=np.asarray(image.convert('RGB'));need(tuple(a[0,0])==(16,16,18),'icon gallery was blurred/tinted after items rendered');scale=2;width,height=640,360;left,top=(width-352)//2,(height-176)//2;bounds=[];unique=[set(),set()]
    for index in range(128):
        x=left+index%16*22+2;y=top+index//16*22+2;pixels=np.asarray(crop(image,scale,x,y,16,16),dtype=int);mask=np.any(np.abs(pixels-(40,44,50))>3,axis=2);ys,xs=np.where(mask)
        need(len(xs)>=6,f'configured icon {index} is empty')
        box=[int(xs.min()),int(ys.min()),int(xs.max()+1),int(ys.max()+1)];need(box[0]>=1 and box[1]>=1 and box[2]<=15 and box[3]<=15,f'configured icon {index} lacks padding: {box}')
        bounds.append(box);unique[index//64].add(hashlib.sha256(pixels.tobytes()).hexdigest())
    need(all(len(values)>=8 for values in unique),f'configured mount models ignored: unique counts {[len(x) for x in unique]}')
    return {'count':128,'bounds':bounds,'distinct_models':[len(x) for x in unique]}


def world(on,off):
    a=np.asarray(on.convert('RGB').crop((560,280,720,440)),dtype=int);b=np.asarray(off.convert('RGB').crop((560,280,720,440)),dtype=int)
    changed=int(np.any(np.abs(a-b)>12,axis=2).sum());need(changed>=15,f'button press/release artwork did not change ({changed})');return changed


def reject(call,label):
    try:call()
    except AssertionError:return
    raise AssertionError('negative accepted: '+label)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--screenshots',type=Path,required=True);parser.add_argument('--output-dir',type=Path,required=True);parser.add_argument('--legacy',type=Path);parser.add_argument('--negative-icons',type=Path);args=parser.parse_args()
    root=args.screenshots;images={name:Image.open(root/f'modern-controls-{name}.png').convert('RGB') for name in ('button-on','button-off','rocker-on','dialog-scale3','dialog-scale2','dialog-edited','tilted','icons')}
    first,second=dialog(images['dialog-scale3'],3),dialog(images['dialog-scale2'],2);scores=[]
    for a,b in zip([first[0]]+first[1],[second[0]]+second[1]):
        score=glyph_overlap(glyph(a),glyph(b));need(score>=.9,f'actual title/button glyphs differ acrossscales ({score})');scores.append(score)
    if args.legacy:
        old=dialog(Image.open(args.legacy),3)
        edited=dialog(images['dialog-edited'],2)
        # The legacy fixture uses +6px/45deg/Right; native editedfixture uses +6px/45deg/Left.
        # Compare the actual matching title, height, tilt and Done regions; direction is checked by live cycling.
        for a,b in [(old[0],first[0]),(old[1][0],edited[1][0]),(old[1][1],edited[1][1]),(old[1][3],first[1][3])]:need(glyph_overlap(glyph(a),glyph(b))>=.9,'matching canonical legacy title/option glyph differs')
    result={'status':'PASS','gui3':first[2],'gui2':second[2],'glyph_overlap':scores,'button_changed_pixels':world(images['button-on'],images['button-off']),'icons':icons(images['icons'])}
    reject(lambda:dialog(images['dialog-scale2'],3),'wrong GUI scale');reject(lambda:dialog(images['button-on'],3),'world asdialog');reject(lambda:world(images['button-off'],images['button-off']),'unchanged button');reject(lambda:icons(Image.new('RGB',(1280,720),(40,44,50))),'blank icons')
    if args.negative_icons:reject(lambda:icons(Image.open(args.negative_icons)),'retained blurred icons')
    args.output_dir.mkdir(parents=True,exist_ok=True);(args.output_dir/'control-pixel-contracts.json').write_text(json.dumps(result,indent=2)+'\n');print('Mounted control pixels PASS: exact dialog geometry/palette/glyphs atbothscales, actualpulseart change and128configured padded icons with distinct mount models;4negativecontrols rejected')


if __name__=='__main__':main()
