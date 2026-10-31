"""Verify Slim Glass reveal contours on all emitted leaf variants."""
import json
from pathlib import Path
from PIL import Image
from import_slim_glass_door import window_edges, windows, inner_frame, m

ROOT=Path(__file__).resolve().parents[1]

def rim(elements):
    return [e for e in elements if len(e['faces'])==1 and next(iter(e['faces'].values()))['texture']=='#door_inner']

def signature(elements):
    return sorted((tuple(e['from']),tuple(e['to']),next(iter(e['faces']))) for e in elements)

# Joined strips have only their exterior contour, including stepped cutouts.
edges=window_edges([(0,0,1,1),(0,1,1,2)])
assert len(edges)==4 and sum(b-a for _,_,a,b in edges)==6
edges=window_edges([(0,0,2,1),(0,1,1,2)])
assert len(edges)==6 and sum(b-a for _,_,a,b in edges)==8
checked=0
for tier in ('low','medium'):
    source=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks/glass_doors'/tier/'slim_glass.png'
    with Image.open(source) as image:panes=windows(image)
    for path in (ROOT/'generated-resources/assets/vandorlabs/models/block/detailed_doors'/tier).glob('space_slim_glass_*_leaf*.json'):
        framed='_framed_' in path.name;paired='_paired_' in path.name;sliding='_sliding_' in path.name;right='_right_' in path.name
        x0=1 if framed else 0;x1=16 if paired or not framed else 15;y0,y1=(1,31) if framed else (0,32);z0,z1=(7,9) if sliding else (12.24,14.24)
        expected=inner_frame(panes,x0,y0,x1,y1,z0,z1)
        if right:expected=m.mirror(expected)
        actual=rim(json.loads(path.read_text())['elements'])
        assert actual and signature(actual)==signature(expected),path.name
        for element in actual:
            face=next(iter(element['faces'].values()))
            assert face['tintindex']==0 and max(face['uv'])<=16,path.name
        checked+=1
    print(tier,'contour faces per leaf:',len(window_edges(panes)))
assert checked==48,checked
print('Slim Glass inner frame PASS:',checked,'emitted rotating/sliding framed/bare paired/solo left/right hinge variants; contiguous contours and native UV scale')
