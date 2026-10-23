#!/usr/bin/env python3
"""Keep full and slab controller map defaults aligned with their listed models."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'src/main/resources/assets/vandorlabs'
GENERATED=ROOT/'generated-resources/assets/vandorlabs'
NAMES=('programmable_ramp','programmable_ramp_slab')
MARKER='# Generated six-face ramp controllers\n'
def asset(path):
    p=GENERATED/path
    return p if p.exists() else SOURCE/path
models=[];blocks=[];textures={}
for name in NAMES:
    variants=json.loads(asset(Path('blockstates')/(name+'.json')).read_text())['variants']
    for key,variant in variants.items():
        state=key.replace('=',':').replace(',','/')
        model=json.loads(asset(Path('models/block')/(variant['model'].split(':')[1]+'.json')).read_text())
        patches={};boxes=[]
        for element in model['elements']:
            # Decorative arrows lie on the main face; the six cube faces carry mode artwork.
            if any(element['from'][i]==element['to'][i] for i in range(3)):continue
            faces=[]
            for face,definition in element['faces'].items():
                texture=definition['texture']
                while texture.startswith('#'):texture=model['textures'][texture[1:]]
                label='ramp_controller_'+texture.split(':')[1].replace('/','_');textures[label]=texture.split(':')[1]+'.png'
                if label not in patches:patches[label]=len(patches)
                faces.append(face[0]+'/'+str(patches[label]))
            rotation=f':R/{variant.get("x",0)}/{variant.get("y",0)}/0' if variant.get('x') or variant.get('y') else ''
            boxes.append(',box='+ '/'.join(f'{v:.6f}' for v in element['from'])+':'+ '/'.join(f'{v:.6f}' for v in element['to'])+':'+':'.join(faces)+rotation)
        models.append(f'modellist:id=%{name},state={state}'+''.join(boxes))
        blocks.append(f'block:id=%{name},state={state}'+''.join(f',patch{i}=0:{tex}' for tex,i in patches.items())+',transparency=SEMITRANSPARENT,stdrot=true')
for filename,lines in [('dynmap-models.txt',models),('dynmap-texture.txt',[f'texture:id={label},filename=assets/vandorlabs/textures/{path},xcount=1,ycount={2 if path.startswith("block/ramp_slab_") else 1}' for label,path in textures.items()]+blocks)]:
    p=SOURCE/filename;old=p.read_text().split(MARKER)[0].splitlines()
    old=[line for line in old if not line.startswith(tuple(prefix+name+',' for prefix in ('modellist:id=%','block:id=%') for name in NAMES))]
    p.write_text('\n'.join(old).rstrip()+'\n\n'+MARKER+'\n'.join(lines)+'\n')
print(f'Generated {len(models)} Ramp Controller Dynmap states')
