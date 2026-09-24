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
removed={'door_airlock_glass','door_security','sliding_airlock_glass','sliding_security_door'}
assert not (removed & by_id.keys()), 'retired standard doors remain in the catalog'
assert not any(id.startswith('detail_') for id in by_id), 'retired detailed doors remain in the catalog'
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
visible=[e for e in catalog if (e['id'].startswith('space_') or e['id']=='programmable_door') and e.get('item')
         and not e.get('hidden') and not e.get('internal_model') and not e['id'].startswith('space_glass_')]
assert {e['id'] for e in visible}=={'programmable_door'}
config=[e for e in catalog if e.get('class')=='BlockConfigurableSpaceDoor']
assert {e['id'] for e in config}=={'programmable_door','space_rotating_door','space_sliding_door'}
assert all(e.get('hidden') for e in config if e['id']!='programmable_door')
assert by_id['programmable_door']['sliding'] is True
assert 'tile.vandorlabs.programmable_door.name=Programmable Door' in (ASSETS/'lang/en_us.lang').read_text()
assert by_id['programmable_glass']['class']=='BlockProgrammableGlass'
assert by_id['programmable_glass']['item']
assert not any(k in by_id for k in ('space_glass_small','space_glass_medium','space_glass_large','framed_observation_glass'))
assert load(ASSETS/'models/item/programmable_glass.json')['parent']=='vandorlabs:item/medium/space_glass_medium'
parts=load(ASSETS/'blockstates/programmable_glass.json')['multipart']
assert len(parts)==48
for part in parts:
    assert any('/'+level+'/' in part['apply']['model'] for level in ('low','medium','high'))
    assert part['when']['AND'][0]['size'] in ('0','1','2')
for level in ('low','medium','high'):
    frame=load(ASSETS/f'models/block/detailed_doors/{level}/space_glass_medium_left.json')
    assert frame['textures']['frame']=='vandorlabs:blocks/space_doors/'+level+'/double_frame_metal'
    assert frame['textures']['inner']=='vandorlabs:blocks/programmable_glass/metal_side'
    assert all(face['texture']=='#frame' for element in frame['elements']
               for side,face in element['faces'].items() if side in ('north','south'))
    assert all(face['texture']=='#inner' for element in frame['elements']
               for side,face in element['faces'].items() if side in ('east','west','up','down'))
    item=load(ASSETS/f'models/item/{level}/space_glass_medium.json')
    pane=next(element for element in item['elements']
              if element['faces'].get('south',{}).get('texture')=='#pane')
    assert pane['from'][2]==7 and pane['to'][2]==9
    assert all(element['from'][2]==6 and element['to'][2]==10
               for element in item['elements'] if element is not pane)
assert load(ASSETS/'models/item/programmable_door.json')['parent'].endswith('space_standard_sliding_door_framed')
hinge_source=load(ROOT/'docs/space-door-pack/hinge/geometry.json')['cuboids']

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
            rails=[e for e in fixed['elements'] if any(f['texture']=='#frame' for f in e['faces'].values())]
            assert len(slabs)==1,(base,hand,'door must be one rectangular slab, no hinge rebate')
            assert len(slabs[0]['faces'])==6,(base,hand,'slab must have six complete faces')
            assert math.isclose(slabs[0]['to'][2]-slabs[0]['from'][2],
                                2),(base,hand,'uniform two-pixel thickness')
            expected_center=8 if door['sliding'] else 13.24
            assert math.isclose(sum((slabs[0]['from'][2],slabs[0]['to'][2]))/2,expected_center)
            width=sum(e['to'][0]-e['from'][0] for e in slabs)
            expected=16 if not door['framed'] else 15 if paired else 14
            assert math.isclose(width,expected)
            if door['framed'] and not paired:
                assert slabs[0]['from'][0]==1 and slabs[0]['to'][0]==15,(base,'single must fill frame')
                if not door['sliding']:
                    # The relief is behind an unbroken, full-width visible border.
                    lip=[r for r in rails if math.isclose(r['from'][2],slabs[0]['to'][2])]
                    assert len(lip)==1 and math.isclose(lip[0]['to'][2]-lip[0]['from'][2],1)
                    assert lip[0]['to'][0]-lip[0]['from'][0]==1
            if paired and not door['sliding']:
                # Inner-jamb removal must not leave daylight at the closed seam.
                assert math.isclose(width,15 if door['framed'] else 16),(base,hand,'closed seam')
                assert (max(e['to'][0] for e in slabs)==16 if hand=='left'
                        else min(e['from'][0] for e in slabs)==0),(base,hand,'seam edge')
            if door['sliding']:
                # Framed leaves clear the inner jamb by one model pixel;
                # bare leaves stay one pixel inside their own block.
                travel=by_id[base]['right_slide' if hand=='right' else 'left_slide'] if paired else (15 if hand=='right' else -15)
                edge=(min(e['from'][0] for e in slabs) if hand=='right' else max(e['to'][0] for e in slabs))+travel
                assert edge==((16 if door['framed'] else 15) if hand=='right' else (0 if door['framed'] else 1)),(base,hand,edge)
            for slab in slabs:
                assert all(slab['faces'][side]['texture']=='#door_inner'
                           and slab['faces'][side]['tintindex']==0
                           for side in ('east','west','up','down'))
                uv=slab['faces']['south']['uv']
                assert math.isclose(abs(uv[2]-uv[0]),slab['to'][0]-slab['from'][0])
                assert math.isclose(abs(uv[3]-uv[1])*2,slab['to'][1]-slab['from'][1])
                if not door['sliding']:
                    pivot=15 if hand=='right' else 1
                    pivot_z=11.24
                    assert by_id[door['paired_model']]['pivot_z']==pivot_z
                    assert by_id[door['paired_model']]['right_pivot' if hand=='right' else 'left_pivot']==pivot
                    sign=-1 if hand=='right' else 1
                    for angle in range(91):
                        c,s=math.cos(math.radians(angle)*sign),math.sin(math.radians(angle)*sign)
                        poly=[(pivot+c*(x-pivot)+s*(z-pivot_z),pivot_z-s*(x-pivot)+c*(z-pivot_z))
                              for x in (slab['from'][0],slab['to'][0]) for z in (slab['from'][2],slab['to'][2])]
                        if angle==90:
                            # Full thickness plus surface-mounted original hardware
                            # can overhang the front by 3.76 model pixels when open.
                            assert all(-1e-7<=x<=16+1e-7 and -3.76-1e-7<=z<=16+1e-7 for x,z in poly),base
                        for rail in rails:
                            if rail['from'][1]==1 and rail['to'][1]==31:
                                assert not overlaps(poly,rail,c,s),(base,hand,angle,'jamb collision')
            for rail in rails:
                depth=rail['to'][2]-rail['from'][2]
                relieved=not door['sliding'] and not paired and rail['from'][1]==1 and (
                        rail['from'][0]>=15 if hand=='left' else rail['to'][0]<=1)
                assert (0<depth<=4+1e-7) if relieved else math.isclose(depth,4)
                assert set(rail['faces'])=={'north','south','east','west','up','down'},(base,'see-through jamb face')
                assert all(rail['faces'][side]['texture']=='#frame'
                           for side in ('north','south'))
                assert all(rail['faces'][side]['texture']=='#door_inner'
                           and rail['faces'][side]['tintindex']==0
                           for side in ('east','west','up','down'))
            if rails:
                assert math.isclose((min(r['from'][2] for r in rails)+max(r['to'][2] for r in rails))/2,expected_center)
            if not door['sliding']:
                for part,model in (('fixed',fixed),('moving',leaf)):
                    hardware=[e for e in model['elements'] if any(f.get('texture')=='#hinge' for f in e['faces'].values())]
                    source=[e for e in hinge_source if e['part']==part]*2
                    assert len(hardware)==len(source),(base,part,'missing or extra hinge parts')
                    for actual,original in zip(hardware,source):
                        for axis in range(3):
                            trim=1 if original['name']=='frame_mount' and axis==0 else 0
                            assert math.isclose(actual['to'][axis]-actual['from'][axis],
                                                original['to'][axis]-original['from'][axis]-trim),(base,original['name'],'hinge dimensions')
                        if original['name']=='door_mount':
                            assert math.isclose(actual['to'][2],slabs[0]['from'][2]),(base,'hinge not on leaf surface')
                        if original['name'] in ('pin','rotating_sleeve'):
                            assert math.isclose((actual['to'][0]+actual['from'][0])/2,pivot)
                            assert math.isclose((actual['to'][2]+actual['from'][2])/2,pivot_z)
                assert len(leaf['elements'])==7,(base,'extra leaf pieces or internal walls')
            else:
                assert not any(any(f.get('texture')=='#hinge' for f in e['faces'].values())
                               for e in fixed['elements']+leaf['elements']),(base,'sliding hinge')
            for level in ('low','medium','high'):
                for part in ('fixed','leaf')+ (('glass',) if any(f'_{d}_' in base for d in ('observation','viewport','laboratory','glazed_hangar')) else ()):
                    path=ASSETS/f'models/item/detailed_doors/{level}/{base}_{hand}_{part}.json'
                    parent=load(path)['parent'].replace('vandorlabs:block/','')
                    model=load(ASSETS/f'models/block/{parent}.json')
                    if part in ('fixed','leaf'):
                        assert model['elements']==(fixed if part=='fixed' else leaf)['elements'],(base,level,'tier geometry mismatch')
                        if not door['sliding']:
                            bare=load(ASSETS/f'models/block/detailed_doors/{level}/{base}_{hand}_{part}_no_hinges.json')
                            expected=[e for e in model['elements'] if not all(f['texture']=='#hinge' for f in e['faces'].values())]
                            assert bare['elements']==expected,(base,'hinge toggle changed slab or frame')
                    else:
                        assert len(model['elements'])==1,(base,level,'stepped glass geometry')
                    for tex in model['textures'].values():
                        texture=ROOT/'texture-packs/default/assets/vandorlabs/textures'/(tex.split(':')[1]+'.png')
                        assert texture.is_file(),texture

for path in (ASSETS/'models/block/detailed_doors').glob('space_glass_medium_*.json'):
    for e in load(path)['elements']:
        if e['faces'].get('south',{}).get('texture')=='#frame':
            assert math.isclose(e['to'][2]-e['from'][2],4)

# Vanilla's atlas interpolation does not wrap UVs beyond 16: those values
# sample neighboring sprites (for example terrain blocks) at door edges.
for path in (ASSETS/'models').rglob('space_*.json'):
    model_data=load(path)
    for element in model_data.get('elements',[]):
        for face in element['faces'].values():
            if face['texture']=='#door_inner':
                assert all(0<=value<=16 for value in face['uv']),(path,face['uv'])

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
print('Space doors PASS: centered 2px rectangular leaves in 4px frames, solid jamb faces, shortened hinges, sealed seams, native UVs, all 91 swing angles and three model tiers')
