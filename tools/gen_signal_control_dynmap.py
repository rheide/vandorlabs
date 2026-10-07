#!/usr/bin/env python3
"""Emit static listed-state defaults for the four-detent mounted controls.

Rotated grip cuboids use conservative bounds; tile configuration stays in game.
"""
import itertools,json,math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/vandorlabs'
MARKER='# Generated signal control models\n'
models=[];blocks=[];textures={}
for entry in json.loads((ROOT/'generated-resources/assets/vandorlabs/data/blocks.json').read_text()):
    if entry.get('class') not in ('BlockSignalControl','BlockTwinPowerLever'):continue
    name=entry['id']
    states=json.loads((ASSETS/'blockstates'/f'{name}.json').read_text())['variants']
    # Dynmap can resolve several listed levels to the same metadata state.
    # Keep texture slots stable across those definitions, including inactive art.
    palette=set()
    for variant in states.values():
        source=json.loads((ASSETS/'models/block'/(variant['model'].split(':')[1]+'.json')).read_text())
        for element in source['elements']:
            for face in element['faces'].values():
                tex=face['texture']
                while tex.startswith('#'):tex=source['textures'][tex[1:]]
                tex=tex.split(':')[1];palette.add('signal_'+tex.replace('/','_'))
    patches={label:i for i,label in enumerate(sorted(palette))}
    for state,variant in states.items():
        props=dict(part.split('=') for part in state.split(','))
        if props.get('on')=='true':continue # This property does not change geometry.
        state='/'.join(f'{k}:{v}' for k,v in props.items() if k!='on')
        model=json.loads((ASSETS/'models/block'/(variant['model'].split(':')[1]+'.json')).read_text())
        boxes=[]
        for element in model['elements']:
            lo=element['from'];hi=element['to']
            if 'rotation' in element:
                r=element['rotation'];axis='xyz'.index(r['axis']);a=(axis+1)%3;b=(axis+2)%3;t=math.radians(r['angle']);points=[]
                for point in itertools.product(*zip(lo,hi)):
                    v=[point[i]-r['origin'][i] for i in range(3)];v[a],v[b]=v[a]*math.cos(t)-v[b]*math.sin(t),v[a]*math.sin(t)+v[b]*math.cos(t)
                    points.append([v[i]+r['origin'][i] for i in range(3)])
                lo=[min(p[i] for p in points) for i in range(3)];hi=[max(p[i] for p in points) for i in range(3)]
            faces=[]
            for face,key in [('east','e'),('up','u'),('north','n'),('south','s'),('down','d'),('west','w')]:
                tex=element['faces'][face]['texture']
                while tex.startswith('#'):tex=model['textures'][tex[1:]]
                tex=tex.split(':')[1];label='signal_'+tex.replace('/','_');textures[label]=tex+'.png'
                if label not in patches:patches[label]=len(patches)
                faces.append(key+'/'+str(patches[label]))
            boxes.append(',box='+ '/'.join(f'{v:.6f}' for v in lo)+':'+ '/'.join(f'{v:.6f}' for v in hi)+':'+':'.join(faces)+(f':R/0/{variant["y"]}/0' if variant.get('y') else ''))
        models.append(f'modellist:id=%{name},state={state}'+''.join(boxes))
        blocks.append(f'block:id=%{name},state={state}'+''.join(f',patch{i}=0:{tex}' for tex,i in patches.items())+',transparency=TRANSPARENT,stdrot=true')
for filename,lines in [('dynmap-models.txt',models),('dynmap-texture.txt',[f'texture:id={label},filename=assets/vandorlabs/textures/{path},xcount=1,ycount=1' for label,path in textures.items()]+blocks)]:
    path=ASSETS/filename;path.write_text(path.read_text().split(MARKER)[0].rstrip()+'\n\n'+MARKER+'\n'.join(lines)+'\n')
print(f'Generated {len(models)} signal control Dynmap states')
