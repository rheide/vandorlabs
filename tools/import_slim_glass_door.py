#!/usr/bin/env python3
"""Import mirrorable Slim Glass door artwork with the shipped glass shimmer pane."""
from pathlib import Path
import argparse, copy, importlib.util, json, shutil
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('doors',ROOT/'tools/import_space_doors.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)

def windows(image):
    """Merge transparent runs inside the frame; exterior alpha remains an empty cutout."""
    width,height=image.size;alpha=image.convert('RGBA').getchannel('A');runs=[]
    for y in range(1,height-1):
        xs=[x for x in range(1,width-1) if alpha.getpixel((x,y))==0]
        if not xs:continue
        start=min(xs);end=max(xs)+1
        if start==1 or end==width-1:continue
        if runs and runs[-1][1]==y and runs[-1][2:]==[start,end]:runs[-1][1]=y+1
        else:runs.append([y,y+1,start,end])
    return [(x0*16/width,(height-y1)*32/height,x1*16/width,(height-y0)*32/height) for y0,y1,x0,x1 in runs]

def window_edges(panes):
    """Contour the union of transparent runs, excluding shared/internal edges."""
    groups={}
    for xa,ya,xb,yb in panes:
        for face,plane,start,end in [('east',xa,ya,yb),('west',xb,ya,yb),('up',ya,xa,xb),('down',yb,xa,xb)]:
            remaining=[(start,end)]
            for na,nb,nc,nd in panes:
                adjacent=nc==plane if face=='east' else na==plane if face=='west' else nd==plane if face=='up' else nb==plane
                if not adjacent:continue
                lo,hi=(nb,nd) if face in ('east','west') else (na,nc)
                pieces=[]
                for a,b in remaining:
                    if hi<=a or lo>=b:pieces.append((a,b));continue
                    if lo>a:pieces.append((a,lo))
                    if hi<b:pieces.append((hi,b))
                remaining=pieces
            groups.setdefault((face,plane),[]).extend(remaining)
    result=[]
    for (face,plane),segments in sorted(groups.items()):
        merged=[]
        for a,b in sorted(segments):
            if merged and a<=merged[-1][1]:merged[-1]=(merged[-1][0],max(b,merged[-1][1]))
            else:merged.append((a,b))
        result.extend((face,plane,a,b) for a,b in merged)
    return result

def inner_frame(panes,x0,y0,x1,y1,z0,z1):
    """Opaque reveal walls follow the authored openings through the leaf depth."""
    elements=[]
    for face,plane,a,b in window_edges(panes):
        if face in ('east','west'):
            x=x0+plane*(x1-x0)/16;a=y0+a*(y1-y0)/32;b=y0+b*(y1-y0)/32
            start,end=[x,a,z0],[x,b,z1];uv=[0,0,z1-z0,b-a]
        else:
            y=y0+plane*(y1-y0)/32;a=x0+a*(x1-x0)/16;b=x0+b*(x1-x0)/16
            start,end=[a,y,z0],[b,y,z1];uv=[0,0,b-a,z1-z0]
        elements.append({'from':start,'to':end,'shade':False,'faces':{face:{'texture':'#door_inner','uv':uv,'tintindex':0}}})
    return elements

def generate(source=None):
    assets=ROOT/'generated-resources/assets/vandorlabs'
    geometry=json.loads((ROOT/'docs/space-door-pack/hinge/geometry.json').read_text())
    for tier,folder in [('low','128x256'),('medium','256x512')]:
        dest=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks/glass_doors'/tier/'slim_glass.png'
        if source is not None:
            source_file=source/folder/'glass_door_left.png'
            left=Image.open(source_file).convert('RGBA');right=Image.open(source/folder/'glass_door_right.png').convert('RGBA')
            from PIL import ImageOps
            assert ImageOps.mirror(left).tobytes()==right.tobytes(),'right leaf is not the exact mirror'
            dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source_file,dest)
        else:left=Image.open(dest).convert('RGBA')
        panes=windows(left)
        for sliding in [False,True]:
            for framed in [False,True]:
                for paired in [False,True]:
                    x0=1 if framed else 0;x1=16 if paired or not framed else 15;y0,y1=(1,31) if framed else (0,32)
                    z0,z1=(7,9) if sliding else (12.24,14.24)
                    # Fit the entire artwork into the frame opening instead of cropping its
                    # outer rails. Keep the existing slab bounds and hinge pivot unchanged.
                    leaf=[m.plate(x0,ya,x1,yb,'leaf',[0,v0,16,v1],z0,z1)
                          for ya,yb,v0,v1 in [(y0,16,8,16),(16,y1,0,8)]]
                    leaf+=inner_frame(panes,x0,y0,x1,y1,z0,z1)
                    glass=[]
                    for xa,ya,xb,yb in panes:
                        # The glazing must follow the same inset as the leaf artwork.
                        xa,xb=x0+xa*(x1-x0)/16,x0+xb*(x1-x0)/16
                        ya,yb=y0+ya*(y1-y0)/32,y0+yb*(y1-y0)/32
                        if xa>=xb or ya>=yb:continue
                        # Repeat the existing square glass material once per world block.
                        cuts=sorted({ya,yb,*[v for v in [16] if ya<v<yb]})
                        for a,b in zip(cuts,cuts[1:]):
                            uv=[xa,16-(b%16 or 16),xb,16-(a%16)]
                            pane=m.plate(xa,a,xb,b,'pane',uv,(z0+z1)/2-.04,(z0+z1)/2+.04)
                            pane['faces']={k:v for k,v in pane['faces'].items() if k in ('north','south')};glass.append(pane)
                    fixed=m.frame(paired=paired,rotating=not sliding) if framed else []
                    if not sliding:fixed+=m.hinges(geometry,'fixed',framed);leaf+=m.hinges(geometry,'moving',framed)
                    for right_hand in [False,True]:
                        base='space_slim_glass'+('_sliding_' if sliding else '_rotating_')+('framed' if framed else 'bare')+('_paired' if paired else '')+('_right_' if right_hand else '_left_')
                        for part,elements in [('fixed',fixed),('leaf',leaf),('glass',glass)]:
                            elements=m.mirror(elements) if right_hand else copy.deepcopy(elements)
                            for no_hinge in ([False,True] if not sliding and part!='glass' else [False]):
                                name=base+part+('_no_hinges' if no_hinge else '')
                                es=[e for e in elements if not all(f['texture']=='#hinge' for f in e['faces'].values())] if no_hinge else elements
                                model=m.model(es);model['textures'].update(leaf='vandorlabs:blocks/glass_doors/'+tier+'/slim_glass',particle='vandorlabs:blocks/glass_doors/'+tier+'/slim_glass',pane='vandorlabs:blocks/space_doors/'+tier+'/glass_tile',door_inner='vandorlabs:blocks/programmable_glass/metal_side',frame='vandorlabs:blocks/space_doors/'+tier+'/double_frame_metal',hinge='vandorlabs:blocks/space_doors/'+tier+'/hinge')
                                for e in model['elements']:
                                    for side in ('east','west','up','down'):
                                        face=e['faces'].get(side)
                                        if face and face['texture'] in ('#edge','#leaf_side'):
                                            face['texture']='#door_inner';face['tintindex']=0
                                for location,content in [(assets/'models/block/detailed_doors'/tier/(name+'.json'),model),(assets/'models/item/detailed_doors'/tier/(name+'.json'),{'parent':'vandorlabs:block/detailed_doors/'+tier+'/'+name})]:
                                    location.parent.mkdir(parents=True,exist_ok=True);location.write_text(json.dumps(content,indent=2)+'\n')
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('source',nargs='?',type=Path,help='Optional source export; omit to rebuild models from bundled artwork');args=parser.parse_args();generate(args.source)
