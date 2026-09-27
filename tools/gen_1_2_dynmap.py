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
for name in ['luxury_seat','military_seat']:
    for face,rot in [('north',0),('east',90),('south',180),('west',270)]:
        for part in ['single','left','middle','right']:
            emit(name,f'facing:{face}/part:{part}/upper:false',load_model('seating/'+name+'_'+part),rot)
for name in ['landing_gear_top_small','landing_gear_top_large','landing_gear_side_small','landing_gear_side_large','landing_gear_top_small_telescopic']:
    for face,rot in [('north',0),('east',90),('south',180),('west',270)]:
        telescopic=name.endswith('telescopic')
        for extended in ([False,True] if telescopic else [False]):
            model=name+('_extended' if extended else '_retracted') if telescopic else name
            state=f'facing:{face}'+(f'/extended:{str(extended).lower()}/lower:false' if telescopic else '')
            emit(name,state,load_model(model),rot)
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
for filename,lines in [('dynmap-models.txt',models),('dynmap-texture.txt',[f'texture:id={k},filename=assets/vandorlabs/textures/{v},xcount=1,ycount=1' for k,v in textures.items()]+blocks)]:
    p=assets/filename;p.write_text(p.read_text().split(marker)[0].rstrip()+'\n\n'+marker+'\n'.join(lines)+'\n')
