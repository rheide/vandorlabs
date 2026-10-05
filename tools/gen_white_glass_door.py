#!/usr/bin/env python3
"""Generate a pale moving leaf rim with a large glazed center from shipped materials."""
from pathlib import Path
import json,importlib.util,copy
root=Path(__file__).resolve().parents[1]
# Use already shipped white material and a pane, rather than an opaque leaf with a glass overlay.
spec=importlib.util.spec_from_file_location('doors',root/'tools/import_space_doors.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
geometry=json.loads((root/'docs/space-door-pack/hinge/geometry.json').read_text())

def tiled_box(bounds, texture):
    """Crop square material art at a constant density, repeating every block."""
    x0,y0,z0,x1,y1,z1=bounds
    def spans(lo,hi):
        while lo<hi:
            end=min(hi,lo+16)
            yield lo,end
            lo=end
    result=[]
    for xa,xb in spans(x0,x1):
        for ya,yb in spans(y0,y1):
            for za,zb in spans(z0,z1):
                element=m.box(xa,ya,za,xb,yb,zb,texture,[0,0,1,1])
                # A UV unit represents one model pixel on every face.
                for side in ('north','south'):
                    element['faces'][side]['uv']=[0,0,xb-xa,yb-ya]
                for side in ('east','west'):
                    element['faces'][side]['uv']=[0,0,zb-za,yb-ya]
                for side in ('up','down'):
                    element['faces'][side]['uv']=[0,0,xb-xa,zb-za]
                result.append(element)
    return result

def handles(x, z0, z1):
    """One shallow rectangular handle per face, with the same visible footprint."""
    result=[]
    for front in (True,False):
        za,zb=(z0-.15,z0) if front else (z1,z1+.15)
        result+=tiled_box((x-.4,13,za,x+.4,19.6,zb),'handle')
    return result

for level in ['low','medium','high']:
 for sliding in [False,True]:
  for framed in [False,True]:
   for paired in [False,True]:
    x0=1 if framed else 0;x1=16 if paired or not framed else 15;y0,y1=(1,31) if framed else (0,32)
    z0,z1=(7,9) if sliding else (12.24,14.24);rim=1.25
    leaf=[]
    for bounds in [(x0,y0,z0,x0+rim,y1,z1),(x1-rim,y0,z0,x1,y1,z1),(x0+rim,y0,z0,x1-rim,y0+rim,z1),(x0+rim,y1-rim,z0,x1-rim,y1,z1)]:
        leaf+=tiled_box(bounds,'white')
    leaf+=handles(x1-rim/2,z0,z1)
    glass=tiled_box((x0+rim,y0+rim,(z0+z1)/2-.04,x1-rim,y1-rim,(z0+z1)/2+.04),'pane')
    fixed=m.frame(paired=paired,rotating=not sliding) if framed else []
    if not sliding:fixed+=m.hinges(geometry,'fixed',framed);leaf+=m.hinges(geometry,'moving',framed)
    for right in [False,True]:
     base='space_white_glass_'+('sliding' if sliding else 'rotating')+'_'+('framed' if framed else 'bare')+('_paired' if paired else '')+('_right_' if right else '_left_')
     for part,elements in [('fixed',fixed),('leaf',leaf),('glass',glass)]:
      elements=m.mirror(elements) if right else copy.deepcopy(elements)
      for no_hinge in ([False,True] if not sliding and part!='glass' else [False]):
       name=base+part+('_no_hinges' if no_hinge else '')
       es=[e for e in elements if not all(f['texture']=='#hinge' for f in e['faces'].values())] if no_hinge else elements
       model=m.model(es);model['textures']['white']='vandorlabs:blocks/light_alloy_hull';model['textures']['handle']='vandorlabs:blocks/dark_wall_panel';model['textures']['particle']='vandorlabs:blocks/light_alloy_hull';model['textures']['pane']='vandorlabs:blocks/space_doors/'+level+'/glass_tile';model['textures']['frame']='vandorlabs:blocks/space_doors/'+level+'/double_frame_metal';model['textures']['hinge']='vandorlabs:blocks/space_doors/'+level+'/hinge'
       path=root/'generated-resources/assets/vandorlabs/models/block/detailed_doors'/level/(name+'.json');path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(model,indent=2)+'\n')
       path=root/'generated-resources/assets/vandorlabs/models/item/detailed_doors'/level/(name+'.json');path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps({'parent':'vandorlabs:block/detailed_doors/'+level+'/'+name},indent=2)+'\n')
