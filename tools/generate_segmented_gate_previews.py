#!/usr/bin/env python3
"""Picker-only orthographic previews from the imported front faces and material pixels."""
from pathlib import Path
import argparse,io,json,math
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'texture-packs/additional/assets/vandorlabs/textures'

def preview(kind):
    model=json.loads((ROOT/f'src/main/resources/assets/vandorlabs/models/block/segmented_gates/{kind}_single.json').read_text())
    image=Image.new('RGBA',(128,128)); pixels=image.load(); textures={}
    for element in sorted(model['elements'],key=lambda e:e['from'][2],reverse=True):
        face=element['faces'].get('north')
        if face is None:continue
        texture=face['texture']
        while texture.startswith('#'):texture=model['textures'][texture[1:]]
        if texture not in textures:
            with Image.open(BASE/(texture.split(':',1)[1]+'.png')) as original:textures[texture]=original.convert('RGBA')
        source=textures[texture];uv=face['uv'];lo,hi=element['from'],element['to'];rotation=element.get('rotation',{})
        if rotation.get('axis','z')!='z' or rotation.get('rescale',False) or face.get('rotation',0):raise ValueError('Unsupported preview rotation')
        angle=math.radians(rotation.get('angle',0));c,s=math.cos(angle),math.sin(angle);ox,oy=rotation.get('origin',[0,0,0])[:2]
        for py in range(8,120):
            y=(120-py-.5)/7
            for px in range(8,120):
                x=(px-8+.5)/7;dx,dy=x-ox,y-oy;lx=ox+c*dx+s*dy;ly=oy-s*dx+c*dy
                if not(lo[0]<=lx<=hi[0] and lo[1]<=ly<=hi[1]):continue
                u=uv[0]+(hi[0]-lx)/(hi[0]-lo[0])*(uv[2]-uv[0]);v=uv[1]+(hi[1]-ly)/(hi[1]-lo[1])*(uv[3]-uv[1])
                pixels[px,py]=source.getpixel((min(source.width-1,max(0,int(u/16*source.width))),min(source.height-1,max(0,int(v/16*source.height)))))
    output=io.BytesIO();image.save(output,format='PNG',optimize=True);return output.getvalue()

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args()
    for kind in ('picket','industrial','lattice'):
        path=BASE/f'blocks/segmented_gates/previews/{kind}.png';data=preview(kind)
        if args.check:
            if not path.is_file() or path.read_bytes()!=data:raise SystemExit('Outdated gate preview: '+str(path.relative_to(ROOT)))
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    print('Segmented gate picker previews PASS three authored front-face previews,128x128,8px framing')
if __name__=='__main__':main()
