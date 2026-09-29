#!/usr/bin/env python3
"""Generate static Dynmap defaults for the imported 1.2 models.

Dynamic tile settings/animation remain an in-game feature; gear uses its retracted pose.
"""
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
assets=root/'src/main/resources/assets/vandorlabs'
marker='# Generated 1.2 model defaults\n'
models=[];blocks=[];textures={}
def load_model(name, item=False):
    return json.loads((assets/('models/item' if item else 'models/block')/(name+'.json')).read_text())
def emit(name,state,data,rotation=0):
    refs=data.get('textures',{})
    patches={}
    boxes=[]
    for e in data.get('elements',[]):
        faces=[]
        for face,key in [('east','e'),('up','u'),('north','n'),('south','s'),('down','d'),('west','w')]:
            tex=e.get('faces',{}).get(face,{}).get('texture','#particle')
            while tex.startswith('#'):tex=refs.get(tex[1:],'vandorlabs:blocks/dark_wall_panel')
            tex=tex.split(':')[-1]
            label='v12_'+tex.replace('/','_')
            textures[label]=tex+'.png'
            if label not in patches:patches[label]=len(patches)
            faces.append(key+'/'+str(patches[label]))
        a='/'.join(str(v) for v in e['from']);b='/'.join(str(v) for v in e['to'])
        boxes.append(',box='+a+':'+b+':'+':'.join(faces)+(f':R/0/{rotation}/0' if rotation else ''))
    # Upper seat cells have no geometry; lower cells include the tall backrest.
    if not boxes:return
    models.append(f'modellist:id=%{name},state={state}'+''.join(boxes))
    blocks.append(f'block:id=%{name},state={state}'+''.join(f',patch{i}=0:{tex}' for tex,i in patches.items())+',transparency=TRANSPARENT,stdrot=true')

def fallback(name, state, bounds, rotation=0):
    """Give Dynmap geometry for blocks whose Minecraft JSON has no elements."""
    data={'textures':{'wall':'vandorlabs:blocks/dark_wall_panel'},'elements':[
        {'from':list(lo),'to':list(hi),'faces':{
            side:{'texture':'#wall'} for side in ('east','up','north','south','down','west')}}
        for lo,hi in bounds]}
    emit(name,state,data,rotation)

for name in ('programmable_block','programmable_light',
             'programmable_trigger_block'):
    for facing in ('north','east','south','west','up','down'):
        fallback(name,'facing:'+facing,[((0,0,0),(16,16,16))])
models.extend([
    'customblock:id=%programmable_slab,state=*,class=com.vandorlabs.dynmap.ProgrammableSlabRenderer',
    'customblock:id=%programmable_door,state=*,class=com.vandorlabs.dynmap.ProgrammableDoorRenderer',
    'customblock:id=%controlled_ramp,state=*,class=com.vandorlabs.dynmap.ControlledRampRenderer',
])
blocks.extend([
    'block:id=%programmable_slab,state=*,patch0=0:v12_blocks_dark_wall_panel,transparency=SEMITRANSPARENT',
    'block:id=%programmable_door,state=*,patch0=0:space_standard,transparency=TRANSPARENT',
])
finishes=[value for value in __import__('re').findall(
    r'new Finish\("[^"]+", "([^"]+)"\)',
    (root/'src/main/java/com/vandorlabs/tiles/ScreenHousingTextures.java').read_text())]
assert len(finishes)==28
for finish in finishes:
    textures['v12_blocks_'+finish.replace('/','_')]='blocks/'+finish+'.png'
textures['v12_source_stone']='assets/minecraft/textures/blocks/stone.png'
blocks.append('block:id=%controlled_ramp,state=*,'+
              ','.join('patch%d=0:v12_blocks_%s' % (i,finish.replace('/','_'))
                       for i,finish in enumerate(finishes))+
              ',patch28=0:v12_source_stone,transparency=SEMITRANSPARENT')

wall_states=json.loads((assets/'blockstates/programmable_wall.json').read_text())['variants']
for name in ('programmable_wall','programmable_porthole_wall',
             'programmable_porthole_block',
             'programmable_diagonal_wall'):
    for state,variant in wall_states.items():
        props=dict(item.split('=') for item in state.split(','))
        rotation=variant.get('y',0)
        if name=='programmable_diagonal_wall':
            # The default diagonal spans six pixels front to back. Tile data
            # can widen or fill it; Dynmap's static model cannot read that.
            bounds=[]
            for y in range(16):
                z=6*(15-y if props['inverted']=='true' else y)/16
                bounds.append(((0,y,z),(16,y+1,z+4)))
        elif name=='programmable_porthole_block':
            bounds=[((0,0,0),(3,16,16)),((13,0,0),(16,16,16)),
                    ((3,0,0),(13,3,16)),((3,13,0),(13,16,16))]
        else:
            z=(6,0,12)[int(props['depth'])]
            if name=='programmable_porthole_wall':
                bounds=[((0,0,z),(3,16,z+4)),((13,0,z),(16,16,z+4)),
                        ((3,0,z),(13,3,z+4)),((3,13,z),(13,16,z+4))]
            else:
                bounds=[((0,0,z),(16,16,z+4))]
        fallback(name,state.replace('=',':').replace(',','/'),bounds,rotation)
for name in ['luxury_seat','military_seat']:
    for face,rot in [('north',0),('east',90),('south',180),('west',270)]:
        for part in ['single','left','middle','right']:
            data=load_model('seating/'+name+'_'+part)
            for e in data['elements']:
                for point in ['from','to']:
                    if e[point][1]>=5:e[point][1]+=1
            emit(name,f'facing:{face}/part:{part}/upper:false',data,rot)
for name in ['landing_gear']:
    for face,rot in [('north',0),('east',90),('south',180),('west',270)]:
        for extended in [False,True]:
            emit(name,f'facing:{face}/extended:{str(extended).lower()}/lower:false',load_model(name+'_small_retracted'),rot)
for name in ['programmable_light_frame','programmable_light_slab']:
    data=load_model('configured/'+name+'_porthole_on',True)
    for face,rot in [('north',0),('east',90),('south',180),('west',270)]:
        if name.endswith('slab'):
            for half in ['bottom','top']:
                variant=json.loads(json.dumps(data))
                if half=='top':
                    for e in variant['elements']:
                        e['from'][1]+=8;e['to'][1]+=8
                emit(name,f'facing:{face}/half:{half}',variant,rot)
        else:emit(name,f'facing:{face}',data,rot)

for face,rot in [('north',0),('east',90),('south',180),('west',270)]:
    for upper in [False,True]:
        data=load_model('programmable_diagonal_half_console',True)
        if upper:
            for e in data['elements']:e['from'][1],e['to'][1]=16-e['to'][1],16-e['from'][1]
        emit('programmable_diagonal_half_console',f'facing:{face}/upper:{str(upper).lower()}',data,rot)
        elements=[]
        for y in range(16):
            z=6*(15-y if upper else y)/16
            # Default hexagonal opening, represented in one-pixel bands.
            edge=8 if y<2 or y>=14 else 3 if 5<=y<11 else 3+(5-y if y<5 else y-10)*1.5
            for x0,x1 in ([(0,16)] if edge==8 else [(0,edge),(16-edge,16)]):
                elements.append({'from':[x0,y,z],'to':[x1,y+1,z+4],'faces':{f:{'texture':'#wall'} for f in ['up','down','north','south','east','west']}})
        emit('programmable_diagonal_porthole',f'facing:{face}/inverted:{str(upper).lower()}/depth:0',{'textures':{'wall':'vandorlabs:blocks/dark_wall_panel'},'elements':elements},rot)
for state,variant in json.loads((assets/'blockstates/programmable_stairs.json').read_text())['variants'].items():
    data=load_model(variant['model'].split(':')[1])
    if variant.get('x')==180:
        for e in data['elements']:
            e['from'][1],e['to'][1]=16-e['to'][1],16-e['from'][1]
            e['from'][2],e['to'][2]=16-e['to'][2],16-e['from'][2]
    emit('programmable_stairs',state.replace('=',':').replace(',','/'),data,variant.get('y',0))
for filename,lines in [('dynmap-models.txt',models),('dynmap-texture.txt',[
        f'texture:id={k},filename={v if v.startswith("assets/") else "assets/vandorlabs/textures/"+v},xcount=1,ycount=1'
        for k,v in textures.items()]+blocks)]:
    p=assets/filename
    old=p.read_text().split(marker)[0].splitlines()
    # These are texture choices, not registered blocks. The old block scan
    # emitted them as blocks, causing Dynmap to resolve their names to air.
    texture_only=('dark_wall_panel','light_wall_panel','ribbed_wall')
    old=[line for line in old if not line.startswith(tuple(
        prefix+name+',' for prefix in ('modellist:id=%','block:id=%')
        for name in texture_only))]
    old=[line for line in old if not line.startswith(
        ('modellist:id=%programmable_door,','block:id=%programmable_door,'))]
    if filename=='dynmap-models.txt':
        # The scanner's quarter-pixel emissive pane can disappear under its
        # surrounding trim in Dynmap's ray tracer. Give it one pixel of depth.
        propulsion=('rocket_thruster','ion_drive','plasma_vent','impulse_engine')
        old=[line.replace('/5.750000:','/5.000000:') if line.startswith(tuple(
                'modellist:id=%'+name+',' for name in propulsion)) else line for line in old]
    p.write_text('\n'.join(old).rstrip()+'\n\n'+marker+'\n'.join(lines)+'\n')
