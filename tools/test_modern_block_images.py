#!/usr/bin/env python3
"""Compare programmable cube dialog pixels at matched native GUI scales."""
import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image
from test_modern_screen_images import glyph_overlap, mask, require


def panel(path, scale, name):
    image=Image.open(path).convert('RGB')
    require(image.size==(1280,720),f'{name}: expected 1280x720 capture')
    width,height=(image.width+scale-1)//scale,(image.height+scale-1)//scale
    pw,ph=min(460,width-8),min(332,height-8)
    left,top=(width-pw)//2,(height-ph)//2
    controls,list_x=left+pw-166,left+12
    list_width,list_y=controls-list_x-19,top+54
    rows=max(2,(ph-60)//22)
    pixels=np.asarray(image)
    def sample(x,y):return tuple(pixels[y*scale+scale//2,x*scale+scale//2])
    require(sample(left+1,top+1)==(32,32,40),f'{name}: header palette/placement')
    require(sample(left+1,top+19)==(16,16,18),f'{name}: opaque panel palette/placement')
    require(sample(list_x-1,list_y-1)==(0,0,0),f'{name}: list border')
    selected=[]
    for row in range(rows):
        strip=pixels[(list_y+row*22+1)*scale,list_x*scale:(list_x+list_width)*scale]
        if np.mean(np.all(strip==(42,74,106),axis=1))>.95:selected.append(row)
    require(selected==[rows//2],f'{name}: selected finish is not centered: {selected}')
    row=selected[0]
    def crop(x,y,w,h):return image.crop((x*scale,y*scale,(x+w)*scale,(y+h)*scale)).resize((w,h),Image.Resampling.NEAREST)
    title=crop(left+8,top+5,220,9)
    label=crop(list_x+23,list_y+row*22+7,min(190,list_width-26),9)
    thumbnail=crop(list_x+3,list_y+row*22+3,16,16)
    require(np.unique(np.asarray(thumbnail).reshape(-1,3),axis=0).shape[0]>12,f'{name}: missing authored thumbnail')
    # Category rows have plain dark backgrounds rather than full-width buttons.
    require(sample(list_x+1,list_y+1) in ((10,10,12),(26,26,32)),f'{name}: category row became a widget')
    return {'title':title,'label':label,'thumbnail':thumbnail,'geometry':[left,top,pw,ph,controls,list_x,list_width,rows]}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--legacy',type=Path,required=True)
    parser.add_argument('--modern-scale3',type=Path,required=True)
    parser.add_argument('--modern-scale2',type=Path,required=True)
    parser.add_argument('--output-dir',type=Path,required=True)
    args=parser.parse_args()
    fixtures={'legacy':panel(args.legacy,3,'legacy'),'modern3':panel(args.modern_scale3,3,'modern3'),'modern2':panel(args.modern_scale2,2,'modern2')}
    require(fixtures['legacy']['geometry']==fixtures['modern3']['geometry'],'matched-scale panel/list geometry differs')
    scores={}
    for name in ('modern3','modern2'):
        title=glyph_overlap(mask(fixtures['legacy']['title']),mask(fixtures[name]['title']))
        width=min(fixtures['legacy']['label'].width,fixtures[name]['label'].width)
        label=glyph_overlap(mask(fixtures['legacy']['label'].crop((0,0,width,9)),True),mask(fixtures[name]['label'].crop((0,0,width,9)),True))
        error=float(np.abs(np.asarray(fixtures['legacy']['thumbnail'],dtype=float)-np.asarray(fixtures[name]['thumbnail'],dtype=float)).mean())
        require(title>=.9,f'{name}: title glyphs differ ({title:.3f})')
        require(label>=.9,f'{name}: selected finish label differs ({label:.3f})')
        require(error<=12,f'{name}: canonical thumbnail pixels differ ({error:.3f})')
        scores[name]={'title_overlap':title,'selected_label_overlap':label,'thumbnail_mean_absolute_error':error}
    args.output_dir.mkdir(parents=True,exist_ok=True)
    for name,fixture in fixtures.items():
        for region in ('title','label','thumbnail'):fixture[region].save(args.output_dir/f'{name}-{region}.png')
    (args.output_dir/'block-pixel-contracts.json').write_text(json.dumps({'status':'PASS','geometry':{name:fixture['geometry'] for name,fixture in fixtures.items()},'pixels':scores},indent=2)+'\n')
    print('Block pixel contracts PASS: matched panel/list geometry, palette, categorized rows, selected label/title and canonical thumbnails at GUI scales 3 and 2')


if __name__=='__main__':main()
