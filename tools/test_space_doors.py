#!/usr/bin/env python3
"""Offline contracts for configurable doors, native art and swept geometry."""
import json
import math
import re
import sys
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'generated-resources/assets/vandorlabs'
def load(path): return json.loads(path.read_text())
catalog=load(ASSETS/'data/blocks.json')
by_id={entry['id']:entry for entry in catalog}
doors=[e for e in catalog if e['class']=='BlockSpaceDoor']
assert len(doors)==60
# Keep saved design indices, GUI choices, native sprites and generated art aligned.
from import_space_doors import FAMILIES, GLASS_FAMILIES, texture_name
tile=(ROOT/'src/main/java/com/vandorlabs/tiles/TileEntitySpaceDoor.java').read_text()
designs=re.findall(r'"([a-z_]+)"',re.search(r'DESIGNS=\{(.*?)\};',tile,re.S).group(1))
assert tuple(designs)==FAMILIES
gui=(ROOT/'src/main/java/com/vandorlabs/client/GuiSpaceDoor.java').read_text()
assert len(re.findall(r'"[^"]+"',re.search(r'LABELS=\{(.*?)\};',gui,re.S).group(1)))==len(FAMILIES)
sprites=(ROOT/'src/main/java/com/vandorlabs/client/SpaceDoorTextures.java').read_text()
glass_indices={int(i) for i in re.findall(r'design==(\d+)',re.search(r'hasGlassDesign\(int design\) \{(.*?)\}',tile).group(1))}
assert {designs[i] for i in glass_indices}==set(GLASS_FAMILIES)
for family in FAMILIES:
    for suffix in ('','_metal','_glass') if family in GLASS_FAMILIES else ('',):
        assert '"'+texture_name(family+suffix)+'"' in sprites
visible=[e for e in catalog if e['id'].startswith('space_') and e.get('item')
         and not e.get('hidden') and not e.get('internal_model') and e['id']!='space_glass']
assert {e['id'] for e in visible}=={'space_rotating_door','space_sliding_door'}

def check_condition(c):
    if 'AND' in c or 'OR' in c:
        assert len(c)==1
        for child in next(iter(c.values())): check_condition(child)
    else: assert all(isinstance(v,str) for v in c.values())
for path in (ASSETS/'blockstates').glob('space_*.json'):
    for part in load(path)['multipart']:
        if 'when' in part: check_condition(part['when'])

def overlaps(poly,box,c,s):
    other=[(x,z) for x in (box['from'][0],box['to'][0]) for z in (box['from'][2],box['to'][2])]
    for ax,az in ((1,0),(0,1),(c,-s),(s,c)):
        a=[x*ax+z*az for x,z in poly]; b=[x*ax+z*az for x,z in other]
        if min(max(a),max(b))-max(min(a),min(b))<1e-7: return False
    return True

for door in doors:
    for paired in (False,True):
        base=door['paired_model'] if paired else door['id']
        for hand in ('left','right'):
            fixed=load(ASSETS/f'models/block/detailed_doors/{base}_{hand}_fixed.json')
            leaf=load(ASSETS/f'models/block/detailed_doors/{base}_{hand}_leaf.json')
            slabs=[e for e in leaf['elements'] if e['faces'].get('south',{}).get('texture')=='#leaf']
            rails=[e for e in fixed['elements'] if e['faces']['south']['texture']=='#frame']
            assert max(e['to'][2]-e['from'][2] for e in slabs)>=4.25
            width=sum(e['to'][0]-e['from'][0] for e in slabs)
            expected=(16 if not door['framed'] else 15 if paired else 14)-(0 if door['sliding'] else .26)
            assert math.isclose(width,expected)
            if door['sliding']:
                # Framed leaves clear the inner jamb by one model pixel;
                # bare leaves stay one pixel inside their own block.
                travel=by_id[base]['right_slide' if hand=='right' else 'left_slide'] if paired else (15 if hand=='right' else -15)
                edge=(min(e['from'][0] for e in slabs) if hand=='right' else max(e['to'][0] for e in slabs))+travel
                assert edge==((16 if door['framed'] else 15) if hand=='right' else (0 if door['framed'] else 1)),(base,hand,edge)
            for slab in slabs:
                uv=slab['faces']['south']['uv']
                assert math.isclose(abs(uv[2]-uv[0]),slab['to'][0]-slab['from'][0])
                assert math.isclose(abs(uv[3]-uv[1])*2,slab['to'][1]-slab['from'][1])
                if not door['sliding']:
                    pivot=13.5 if hand=='right' else 2.5
                    sign=-1 if hand=='right' else 1
                    for angle in range(91):
                        c,s=math.cos(math.radians(angle)*sign),math.sin(math.radians(angle)*sign)
                        poly=[(pivot+c*(x-pivot)+s*(z-13.5),13.5-s*(x-pivot)+c*(z-13.5))
                              for x in (slab['from'][0],slab['to'][0]) for z in (slab['from'][2],slab['to'][2])]
                        if angle==90:
                            assert all(-1e-7<=x<=16+1e-7 and -1e-7<=z<=16+1e-7 for x,z in poly),base
                        for rail in rails:
                            if rail['from'][1]==1 and rail['to'][1]==31:
                                assert not overlaps(poly,rail,c,s),(base,hand,angle,'jamb collision')
            for rail in rails:
                assert math.isclose(rail['to'][2]-rail['from'][2],4.45)
                assert all(f['texture']=='#frame' for f in rail['faces'].values())
            for level in ('low','medium','high'):
                for part in ('fixed','leaf')+ (('glass',) if any(f'_{d}_' in base for d in ('observation','viewport','laboratory','glazed_hangar')) else ()):
                    path=ASSETS/f'models/item/detailed_doors/{level}/{base}_{hand}_{part}.json'
                    parent=load(path)['parent'].replace('vandorlabs:block/','')
                    model=load(ASSETS/f'models/block/{parent}.json')
                    for tex in model['textures'].values():
                        texture=ROOT/'texture-packs/default/assets/vandorlabs/textures'/(tex.split(':')[1]+'.png')
                        assert texture.is_file(),texture

for path in (ASSETS/'models/block/detailed_doors').glob('space_glass_*.json'):
    for e in load(path)['elements']:
        if e['faces'].get('south',{}).get('texture')=='#frame':
            assert math.isclose(e['to'][2]-e['from'][2],4.45)

for archive in sys.argv[1:]:
    with zipfile.ZipFile(archive) as z:
        for n in z.namelist():
            for level in ('low','medium','high'):
                if f'/{level}/assets/scifidoors/textures/blocks/' not in n or n.endswith('/'): continue
                source=ROOT/f'texture-packs/space-doors/{level}'/Path(n).name
                assert source.read_bytes()==z.read(n),str(source)
                for tree in ('default','original'):
                    runtime=ROOT/f'texture-packs/{tree}/assets/vandorlabs/textures/blocks/space_doors/{level}'/source.name
                    assert runtime.read_bytes()==source.read_bytes()
print('Space doors PASS: two visible blocks, fifteen designs, three detail sets, thick leaves, native UVs, all 91 swing angles and model references')
