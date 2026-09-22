#!/usr/bin/env python3
"""Import native pack pixels; generate cropped UV geometry, never resample art."""
import copy
import argparse
import json
import math
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'generated-resources/assets/vandorlabs'
PREFIX = 'scifi_industrial_door_pack_v2/'
TEX = 'vandorlabs:blocks/space_doors/'
FAMILIES = ('observation', 'airlock', 'standard', 'security', 'reactor', 'viewport', 'laboratory', 'cargo', 'ventilation',
            'cargo_lift', 'blast_shield', 'glazed_hangar', 'quarantine_seal', 'reactor_barrier', 'modular_shutter')
GLASS_FAMILIES = ('observation','viewport','laboratory','glazed_hangar')
FRAME_DEPTH = 4  # Space frames, in model pixels.
# Original thin-door hardware: pin 1 pixel in front of the mounting face.
# Move it with the inner face of the thick slab, without scaling the hardware.
LEAF_FRONT, LEAF_BACK = 12.24, 14.24
HINGE_X, HINGE_Z = 1, LEAF_FRONT-1
WRITTEN_MODELS = []

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')
    if 'models' in path.parts: WRITTEN_MODELS.append(path)

def box(x0,y0,z0,x1,y1,z1, texture, uv):
    return {'from':[x0,y0,z0], 'to':[x1,y1,z1], 'shade':False,
            'faces':{face:{'texture':'#'+texture,'uv':list(uv)}
                     for face in ('north','south','east','west','up','down')}}

def plate(x0,y0,x1,y1, texture, uv, z0=8,z1=9):
    el = box(x0,y0,z0,x1,y1,z1,'edge',[0,0,1,1])
    el['faces']['south'] = {'texture':'#'+texture,'uv':list(uv)}
    el['faces']['north'] = {'texture':'#'+texture,'uv':[uv[2],uv[1],uv[0],uv[3]]}
    if texture=='frame':
        # Frame extrusion must not magnify the hinge atlas's screw pixels.
        for side in ('east','west','up','down'):
            el['faces'][side]={'texture':'#frame','uv':[.16,4,.28,4.12]}
    return el

def leaf_parts(x0,y0,x1,y1,sliding,texture='leaf'):
    # One rectangular slab, including the hinge side. No rebate or internal wall.
    z0,z1=(7,9) if sliding else (LEAF_FRONT,LEAF_BACK)
    slab=plate(x0,y0,x1,y1,texture,[x0,(32-y1)/2,x1,(32-y0)/2],z0,z1)
    for side in ('east','west','up','down'):
        slab['faces'][side]['texture']='#leaf_side'
        slab['faces'][side]['uv']=[z0,0,z1,16] if side in ('east','west') else [x0,z0,x1,z1]
    return [slab]

def mirror(elements):
    result = copy.deepcopy(elements)
    for e in result:
        e['from'][0], e['to'][0] = 16-e['to'][0],16-e['from'][0]
        f=e['faces']
        f['east'],f['west']=f.get('west'),f.get('east')
        e['faces']={k:v for k,v in f.items() if v is not None}
        for side in ('north','south','up','down'):
            if side in f:
                uv=f[side]['uv']; uv[0],uv[2]=uv[2],uv[0]
    return result

def frame(width=16,height=32,paired=False,rotating=False):
    # Nine-slice at native 128 px/block: a model pixel samples half a UV unit
    # from the 2x2 frame atlas. Clip runs instead of squeezing a square frame.
    result=[]
    for bottom in (False,True):
        y0,y1=(0,1) if bottom else (height-1,height)
        v0,v1=(15.5,16) if bottom else (0,.5)
        result.append(plate(0,y0,1,y1,'frame',[0,v0,.5,v1],8,9))
        end=width if paired else width-1
        result.append(plate(1,y0,end,y1,'frame',[.5,v0,end/2,v1],8,9))
        if not paired:
            result.append(plate(width-1,y0,width,y1,'frame',[15.5,v0,16,v1],8,9))
    result.append(plate(0,1,1,height-1,'frame',[0,.5,.5,(height-1)/2],8,9))
    if not paired:
        result.append(plate(width-1,1,width,height-1,'frame',[15.5,.5,16,(height-1)/2],8,9))
    for element in result:
        element['from'][2] = 11.24 if rotating else 6
        element['to'][2] = element['from'][2] + FRAME_DEPTH
    if rotating and not paired:
        # Two closed solids, not open strips: a shallow rear pocket clears the
        # swept corner, and a full-width front stop seals the closed opening.
        # All six faces are needed: exposed step shoulders are visible from
        # inside the doorway even where the cuboids touch in depth.
        result.pop()  # free jamb; hinge jamb and horizontal rails stay intact
        radius2=(width-1-HINGE_X)**2+(LEAF_BACK-HINGE_Z)**2
        inner=HINGE_X+math.sqrt(radius2)
        result.append(plate(inner,1,width,height-1,'frame',
                            [15.5+(inner-(width-1))/2,.5,16,(height-1)/2],11.24,LEAF_BACK))
        result.append(plate(width-1,1,width,height-1,'frame',
                            [15.5,.5,16,(height-1)/2],LEAF_BACK,11.24+FRAME_DEPTH))
    return result

def hinges(geometry, part, framed):
    elements=[]
    for height in (6,26):
        for c in geometry['cuboids']:
            if c['part'] != part: continue
            a,b=list(c['from']),c['to']; tile=c['material_tile']
            if c['name']=='frame_mount': a[0]+=1  # truncate the frame-side arm
            u=(tile%2)*8+.25; v=(tile//2)*8+.25
            # Rigidly translate the original thin-door hardware onto the new
            # slab face. Fixed pin and moving sleeve share the actual pivot.
            az=LEAF_FRONT-b[2]
            bz=LEAF_FRONT-a[2]
            elements.append(box(a[0]+HINGE_X,a[1]+height,az,
                                b[0]+HINGE_X,b[1]+height,bz,
                                'hinge',[u,v,u+7.5,v+7.5]))
    return elements

def texture_name(family):
    base = family
    for suffix in ('_metal', '_glass'):
        if base.endswith(suffix): base = base[:-len(suffix)]
    prefix = 'lift_' if base in FAMILIES[9:] else 'door_' if base in FAMILIES[5:9] else ''
    return prefix+family

def model(elements, family='standard'):
    glass_family=family.replace('_metal','')
    if glass_family not in GLASS_FAMILIES: glass_family='observation'
    return {'ambientocclusion':False,'textures':{'leaf':TEX+texture_name(family),
            'frame':TEX+'double_frame_metal','edge':TEX+'hinge',
            'leaf_side':'vandorlabs:blocks/wall_panel_dark',
            'hinge':TEX+'hinge','glass':TEX+texture_name(glass_family)+'_glass',
            'pane':TEX+'glass_tile','particle':TEX+texture_name(family)},'elements':elements}

def emit_model(name,elements,family='standard'):
    write(OUT/'models/block/detailed_doors'/f'{name}.json',model(elements,family))

def main(archive, detail, expansion, lift):
    library=ROOT/'texture-packs/space-doors'
    if archive:
        with zipfile.ZipFile(archive) as pack:
            for level in ('low','medium','high'):
                source=PREFIX+level+'/assets/scifidoors/textures/blocks/'
                for name in pack.namelist():
                    if not name.startswith(source) or name.endswith('/'): continue
                    dest=library/level/Path(name).name
                    dest.parent.mkdir(parents=True,exist_ok=True)
                    dest.write_bytes(pack.read(name))
            for name in ('README.md','ASSET_MANIFEST.json','GLASS_REVISION.json','hinge/geometry.json'):
                dest=ROOT/'docs/space-door-pack'/name
                dest.parent.mkdir(parents=True,exist_ok=True)
                dest.write_bytes(pack.read(PREFIX+name))
    if expansion:
        with zipfile.ZipFile(expansion) as pack:
            prefix='scifi_industrial_door_expansion/'
            for level in ('low','medium','high'):
                source=prefix+level+'/assets/scifidoors/textures/blocks/'
                for name in pack.namelist():
                    if name.startswith(source) and not name.endswith('/'):
                        (library/level/Path(name).name).write_bytes(pack.read(name))
            for name in ('README.md','ASSET_MANIFEST.json','VALIDATION.json'):
                dest=ROOT/'docs/space-door-pack/expansion'/name
                dest.parent.mkdir(parents=True,exist_ok=True)
                dest.write_bytes(pack.read(prefix+name))
    if lift:
        with zipfile.ZipFile(lift) as pack:
            prefix='scifi_industrial_lift_doors/'
            for level in ('low','medium','high'):
                source=prefix+level+'/assets/scifidoors/textures/blocks/'
                for name in pack.namelist():
                    if name.startswith(source) and not name.endswith('/'):
                        dest=library/level/Path(name).name
                        dest.parent.mkdir(parents=True,exist_ok=True)
                        dest.write_bytes(pack.read(name))
            for name in ('README.md','ASSET_MANIFEST.json'):
                dest=ROOT/'docs/space-door-pack/lift'/name
                dest.parent.mkdir(parents=True,exist_ok=True)
                dest.write_bytes(pack.read(prefix+name))
    geometry=json.loads((ROOT/'docs/space-door-pack/hinge/geometry.json').read_text())
    for source in (library/detail).iterdir():
        if not source.is_file(): continue
        for tree in ('default','original'):
            dest=ROOT/f'texture-packs/{tree}/assets/vandorlabs/textures/blocks/space_doors'/source.name
            dest.parent.mkdir(parents=True,exist_ok=True)
            dest.write_bytes(source.read_bytes())
    for level in ('low','medium','high'):
        for source in (library/level).iterdir():
            for tree in ('default','original'):
                dest=ROOT/f'texture-packs/{tree}/assets/vandorlabs/textures/blocks/space_doors'/level/source.name
                dest.parent.mkdir(parents=True,exist_ok=True)
                dest.write_bytes(source.read_bytes())

    catalog_path=OUT/'data/blocks.json'
    catalog=[e for e in json.loads(catalog_path.read_text()) if not e['id'].startswith('space_')]
    names=[]
    for family in FAMILIES:
        for sliding in (False,True):
            for framed in (False,True):
                motion='sliding' if sliding else 'rotating'
                id=f'space_{family}_{motion}_'+('framed' if framed else 'bare')
                paired=id+'_paired'
                catalog.append(dict(id=paired,type='space_door_model',
                    **{'class':'BlockDetailedDoor'},item=False,internal_model=True,
                    motion='sliding_door' if sliding else 'door',sliding=sliding,
                    door_layout='double',split_inside_one_block=False,
                    left_pivot=HINGE_X,right_pivot=16-HINGE_X,
                    pivot_z=HINGE_Z,left_angle=90,right_angle=-90,
                    left_slide=-16 if framed else -15,right_slide=16 if framed else 15))
                catalog.append(dict(id=id,type='space_door',**{'class':'BlockSpaceDoor'},
                    item=True,hidden=True,internal_model=family in FAMILIES[5:],sliding=sliding,framed=framed,paired_model=paired))
                names.append((id,'Space '+('Reactor Service' if family=='reactor' else family.replace('_',' ').title())
                    +' '+motion.title()+' Door ('+('Framed' if framed else 'Bare')+')'))
                parts=[]
                for is_pair in (False,True):
                    base=paired if is_pair else id
                    x0=1 if framed else 0
                    x1=16 if is_pair or not framed else 15
                    y0,y1=(1,31) if framed else (0,32)
                    # Sliding slabs have front/back clearance within the frame.
                    leaf=leaf_parts(x0,y0,x1,y1,sliding)
                    fixed=frame(paired=is_pair,rotating=not sliding) if framed else []
                    if not sliding:
                        fixed+=hinges(geometry,'fixed',framed)
                        leaf+=hinges(geometry,'moving',framed)
                    for right in (False,True):
                        hand='right' if right else 'left'
                        f=mirror(fixed) if right else fixed
                        l=mirror(leaf) if right else leaf
                        for suffix,els in (('fixed',f),('leaf',l)):
                            name=f'{base}_{hand}_{suffix}'
                            emit_model(name,els,family+'_metal' if family in GLASS_FAMILIES else family)
                            if not sliding:
                                clean=[e for e in els if not all(f['texture']=='#hinge' for f in e['faces'].values())]
                                emit_model(name+'_no_hinges',clean,family+'_metal' if family in GLASS_FAMILIES else family)
                                write(OUT/'models/item/detailed_doors'/f'{name}_no_hinges.json',
                                      {'parent':'vandorlabs:block/detailed_doors/'+name+'_no_hinges'})
                            if suffix=='leaf':
                                write(OUT/'models/item/detailed_doors'/f'{name}.json',
                                      {'parent':'vandorlabs:block/detailed_doors/'+name})
                        if family in GLASS_FAMILIES:
                            panes=leaf_parts(x0,y0,x1,y1,sliding,'glass')
                            for glass in panes:
                                glass['from'][2]-=.001; glass['to'][2]+=.001
                                glass['faces']={k:v for k,v in glass['faces'].items() if k in ('north','south')}
                            g=mirror(panes) if right else panes
                            name=f'{base}_{hand}_glass'
                            emit_model(name,g,family)
                            write(OUT/'models/item/detailed_doors'/f'{name}.json',
                                  {'parent':'vandorlabs:block/detailed_doors/'+name})
                        for facing,angle in (('south',0),('west',90),('north',180),('east',270)):
                            parts.append({'when':{'facing':facing,'half':'lower',
                                'hinge':'left' if right else 'right','paired':str(is_pair).lower()},
                                'apply':{'model':f'vandorlabs:detailed_doors/{base}_{hand}_fixed','y':angle}})
                write(OUT/'blockstates'/f'{id}.json',{'multipart':parts})
                inventory=model(frame(rotating=not sliding) if framed else [],family)
                ix0,ix1,iy0,iy1=(1,15,1,31) if framed else (0,16,0,32)
                inventory['elements']+=leaf_parts(ix0,iy0,ix1,iy1,sliding)
                if not sliding:
                    inventory['elements']+=hinges(geometry,'fixed',framed)+hinges(geometry,'moving',framed)
                inventory['display']={'gui':{'rotation':[15,205,0],'translation':[0,-4,0],'scale':[.35,.35,.35]},
                    'firstperson_righthand':{'scale':[.2,.2,.2]},'thirdperson_righthand':{'scale':[.2,.2,.2]}}
                write(OUT/'models/item'/f'{id}.json',inventory)
    # Space Glass uses the same calculated connection flags as Observation Glass.
    catalog.append(dict(id='space_glass',type='glass_wall',**{'class':'BlockSpaceGlass'},item=True))
    names.append(('space_glass','Space Glass (Medium)'))
    glass=plate(0,0,16,16,'pane',[0,0,16,16],7.99,8.01)
    glass['faces']={k:v for k,v in glass['faces'].items() if k in ('north','south')}
    emit_model('space_glass_pane',[glass])
    pieces={'left':[plate(0,1,1,15,'frame',[0,.5,.5,7.5])],
            'right':[plate(15,1,16,15,'frame',[15.5,.5,16,7.5])],
            'top':[plate(1,15,15,16,'frame',[.5,0,7.5,.5])],
            'bottom':[plate(1,0,15,1,'frame',[.5,15.5,7.5,16])]}
    parts=[]
    for rotated,angle in ((False,0),(True,90)):
        # The glass itself is drawn after opaque geometry with its faint alpha intact.
        for edge,els in pieces.items():
            for element in els:
                element['from'][2]=6
                element['to'][2]=6+FRAME_DEPTH
            emit_model('space_glass_'+edge,els)
            parts.append({'when':{'rotated':str(rotated).lower(),edge:'true'},
                          'apply':{'model':'vandorlabs:detailed_doors/space_glass_'+edge,'y':angle}})
        for horiz,x in (('left',0),('right',15)):
            for vert,y in (('bottom',0),('top',15)):
                name='space_glass_'+vert+'_'+horiz
                u=0 if x==0 else 15.5; v=0 if y==15 else 15.5
                emit_model(name,[plate(x,y,x+1,y+1,'frame',[u,v,u+.5,v+.5],6,6+FRAME_DEPTH)])
                inner='inner_'+('t' if y else 'b')+('l' if x==0 else 'r')
                parts.append({'when':{'AND':[{'rotated':str(rotated).lower()},
                    {'OR':[{horiz:'true'},{vert:'true'},{inner:'true'}]}]},
                    'apply':{'model':'vandorlabs:detailed_doors/'+name,'y':angle}})
    write(OUT/'blockstates/space_glass.json',{'multipart':parts})
    write(OUT/'models/item/space_glass.json',model([glass]+frame(height=16)))
    # All three detail sets are baked for the two programmable door items.
    # Existing twenty IDs remain registered but hidden for save compatibility.
    originals=list(dict.fromkeys(WRITTEN_MODELS))
    for level in ('low','medium','high'):
        for source in originals:
            relative=source.relative_to(OUT/'models')
            if not source.stem.startswith('space_'): continue
            target=OUT/'models'/relative.parent/level/relative.name
            raw=source.read_text().replace(TEX,TEX+level+'/')
            raw=raw.replace('vandorlabs:block/detailed_doors/space_',
                            'vandorlabs:block/detailed_doors/'+level+'/space_')
            write(target,json.loads(raw))
            if relative.parts[0]=='block' and source.stem.endswith('_fixed'):
                write(OUT/'models/item/detailed_doors'/level/relative.name,
                      {'parent':'vandorlabs:block/detailed_doors/'+level+'/'+source.stem})
    for motion in ('rotating','sliding'):
        id='space_'+motion+'_door'
        catalog.append(dict(id=id,type='space_door',**{'class':'BlockConfigurableSpaceDoor'},
                            item=True,hidden=True,sliding=motion=='sliding',paired_model=f'space_standard_{motion}_framed_paired'))
        names.append((id,'Space '+motion.title()+' Door'))
        write(OUT/'blockstates'/f'{id}.json',{'multipart':[{'apply':{'model':'vandorlabs:detailed_doors/space_empty'}}]})
        write(OUT/'models/item'/f'{id}.json',{'parent':f'vandorlabs:item/space_standard_{motion}_framed'})
    # Retain the old saved id as Medium, with two additional creative items.
    glass_state=json.loads((OUT/'blockstates/space_glass.json').read_text())
    for id,level,label in (('space_glass_small','low','Small'),('space_glass','medium','Medium'),
                           ('space_glass_large','high','Large')):
        if id!='space_glass':
            catalog.append(dict(id=id,type='glass_wall',**{'class':'BlockSpaceGlass'},item=True))
            names.append((id,'Space Glass ('+label+')'))
        state=json.loads(json.dumps(glass_state).replace('detailed_doors/space_glass_',
                                                      'detailed_doors/'+level+'/space_glass_'))
        write(OUT/'blockstates'/f'{id}.json',state)
        write(OUT/'models/item'/f'{id}.json',{'parent':'vandorlabs:item/'+level+'/space_glass'})
    id='space_door'
    catalog.append(dict(id=id,type='space_door',**{'class':'BlockConfigurableSpaceDoor'},
                        item=True,sliding=True,paired_model='space_standard_sliding_framed_paired'))
    names.append((id,'Space Door'))
    write(OUT/'blockstates'/f'{id}.json',{'multipart':[{'apply':{'model':'vandorlabs:detailed_doors/space_empty'}}]})
    write(OUT/'models/item'/f'{id}.json',{'parent':'vandorlabs:item/space_standard_sliding_framed'})
    emit_model('space_empty',[])
    write(catalog_path,catalog)
    lang=OUT/'lang/en_us.lang'
    lines=[line for line in lang.read_text().splitlines() if not line.startswith('tile.vandorlabs.space_')]
    lang.write_text('\n'.join(lines+[f'tile.vandorlabs.{id}.name={name}' for id,name in names])+'\n')
    # Static Dynmap fallback; animated and neighbor-derived geometry is client-side.
    for filename in ('dynmap-models.txt','dynmap-texture.txt'):
        path=ROOT/'src/main/resources/assets/vandorlabs'/filename
        lines=[s for s in path.read_text().splitlines() if 'space_' not in s]
        if filename=='dynmap-texture.txt':
            for family in FAMILIES+('glass_tile',):
                lines.append(f'texture:id=space_{family},filename=assets/vandorlabs/textures/blocks/space_doors/{texture_name(family)}.png,xcount=1,ycount=1')
        for id,_ in names:
            if any(e['id']==id and e.get('internal_model') for e in catalog): continue
            texture='glass_tile' if id.startswith('space_glass') else 'standard' if id in ('space_door','space_rotating_door','space_sliding_door') else id.split('_')[1]
            if filename=='dynmap-texture.txt':
                lines.append(f'block:id=%{id},state=*,transparency=TRANSPARENT,stdrot=true,patch0=0:space_{texture}')
            else:
                lines.append(f'modellist:id=%{id},state=*,box=0/0/8:16/16/9:n/0:s/0:e/0:w/0:u/0:d/0')
        path.write_text('\n'.join(lines)+'\n')
    print('Generated one unified Space door, hidden legacy ids, three native detail sets, and compatibility models.')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive',nargs='?',help='Optional ZIP to refresh all three native sets')
    parser.add_argument('--detail',choices=('low','medium','high'),default='medium')
    parser.add_argument('--expansion',help='Optional expansion ZIP to refresh all three detail sets')
    parser.add_argument('--lift',help='Optional lift-door ZIP to refresh all three detail sets')
    args=parser.parse_args()
    main(args.archive,args.detail,args.expansion,args.lift)
