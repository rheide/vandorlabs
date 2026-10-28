#!/usr/bin/env python3
"""Generate UV-faithful map meshes from the programmer OBJ and native coupler/track JSON.

The programmer is clipped into its four actual reserved cells by the common map
surface renderer. Tracks use their authored standalone/default style, including
when attached to a gear assembly; animation is represented at its static pose.
"""
import argparse
import json
import math
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/vandorlabs'
DATA=ASSETS/'data/dynmap_utility_models.json'
BEGIN='# Generated 2.6 utility overview models\n'
END='# End 2.6 utility overview models\n'
PALETTE='# Generated tile-aware material palette\n'
FACES=('north','east','south','west')
TRACKS=('compact_track','armoured_track','bogie_track')

def rotate(p,axis,angle,origin=(.5,.5,.5)):
    v=[p[i]-origin[i] for i in range(3)];a=(axis+1)%3;b=(axis+2)%3;t=math.radians(angle)
    v[a],v[b]=v[a]*math.cos(t)-v[b]*math.sin(t),v[a]*math.sin(t)+v[b]*math.cos(t)
    return [v[i]+origin[i] for i in range(3)]

def obj(path):
    vertices=[];uv=[];result=[];materials={};material=None
    for line in path.read_text().splitlines():
        fields=line.split()
        if not fields:continue
        if fields[0]=='mtllib':
            current=None
            for entry in (path.parent/fields[1]).read_text().splitlines():
                words=entry.split()
                if not words:continue
                if words[0]=='newmtl':current=words[1]
                elif words[0]=='map_Kd':
                    assert words[1].startswith('vandorlabs:'),words[1]
                    materials[current]=words[1].split(':',1)[1]
        elif fields[0]=='usemtl':material=fields[1]
        elif fields[0]=='v':vertices.append([float(n) for n in fields[1:4]])
        elif fields[0]=='vt':uv.append([float(fields[1]),1-float(fields[2])])
        elif fields[0]=='f':
            face=[]
            for corner in fields[1:]:
                point,tex,*_=corner.split('/');face.append(vertices[int(point)-1]+uv[int(tex)-1])
            assert len(face)==4,'Programmer source is authored as quads'
            result.append((materials[material],face))
    assert len(result)==108,'Programmer casing/screen export changed'
    return result

def model(name):
    data=json.loads((ASSETS/'models/block'/f'{name}.json').read_text());result=[]
    if data.get('parent')=='block/cube':data['elements']=[{'from':[0,0,0],'to':[16,16,16],'faces':{face:{'texture':'#'+face,'uv':[0,0,16,16]} for face in ('north','south','east','west','up','down')}}]
    for element in data['elements']:
        x,y,z=[n/16 for n in element['from']];X,Y,Z=[n/16 for n in element['to']]
        corners={
            'north':[[X,Y,z],[X,y,z],[x,y,z],[x,Y,z]],
            'south':[[x,Y,Z],[x,y,Z],[X,y,Z],[X,Y,Z]],
            'west':[[x,Y,z],[x,y,z],[x,y,Z],[x,Y,Z]],
            'east':[[X,Y,Z],[X,y,Z],[X,y,z],[X,Y,z]],
            'up':[[x,Y,z],[x,Y,Z],[X,Y,Z],[X,Y,z]],
            'down':[[x,y,Z],[x,y,z],[X,y,z],[X,y,Z]]}
        for side,definition in element['faces'].items():
            tex=definition['texture']
            for _ in range(20):
                if not tex.startswith('#'):break
                tex=data['textures'][tex[1:]]
            assert tex.startswith('vandorlabs:')
            u0,v0,u1,v1=[n/16 for n in definition['uv']];uv=[[u0,v0],[u0,v1],[u1,v1],[u1,v0]]
            turns=definition.get('rotation',0)//90
            if turns:uv=uv[-turns:]+uv[:-turns]
            polygon=[]
            for point,st in zip(corners[side],uv):
                if 'rotation' in element:
                    r=element['rotation'];assert not r.get('rescale',False)
                    point=rotate(point,'xyz'.index(r['axis']),r['angle'],[n/16 for n in r['origin']])
                polygon.append(point+st)
            result.append((tex.split(':',1)[1],polygon))
    return result

def transformed(source,x=0,y=0,offset=(0,0,0)):
    result=[]
    for tex,polygon in source:
        moved=[]
        for vertex in polygon:
            p=vertex[:3]
            if x:p=rotate(p,0,-x)
            if y:p=rotate(p,1,-y)
            moved.append([round(p[i]-offset[i],10) for i in range(3)]+vertex[3:])
        result.append((tex,moved))
    return result

def payload():
    states={};programmer=obj(ASSETS/'models/block/data_chip_programmer/ready/complete.obj')
    for facing,yaw in zip(FACES,(0,90,180,270)):
        direction={'north':(1,0,0),'east':(0,0,1),'south':(-1,0,0),'west':(0,0,-1)}[facing]
        for part in range(4):
            offset=(direction[0]*(part&1),part>>1,direction[2]*(part&1))
            states[f'data_chip_programmer/{facing}/{part}']=transformed(programmer,y=yaw,offset=offset)
    for coupler in ('square_coupler','vehicle_coupler_slab'):
        variants=json.loads((ASSETS/f'blockstates/{coupler}.json').read_text())['variants']
        for props,variant in variants.items():
            state=dict(p.split('=') for p in props.split(','));key=f'{coupler}/{state["facing"]}/{state["locked"]}'
            if coupler=='vehicle_coupler_slab':key+='/'+state['half']
            states[key]=transformed(model(variant['model'].split(':',1)[1]),variant.get('x',0),variant.get('y',0))
    variants=json.loads((ASSETS/'blockstates/redstone_transmitter_block.json').read_text())['variants']
    for props,variant in variants.items():
        facing=dict(p.split('=') for p in props.split(','))['facing']
        states[f'redstone_transmitter_block/{facing}']=transformed(model('redstone_transmitter'),variant.get('x',0),variant.get('y',0))
    for name in TRACKS:
        source=model(name)
        for facing,yaw in zip(FACES,(0,90,180,270)):states[f'{name}/{facing}']=transformed(source,y=yaw)
    textures=sorted({texture for faces in states.values() for texture,_ in faces});slots={tex:i for i,tex in enumerate(textures)}
    return {'textures':textures,'states':{key:[[slots[tex],*polygon] for tex,polygon in faces] for key,faces in sorted(states.items())}}

def definitions(text,lines):
    if BEGIN in text:
        before,section=text.split(BEGIN,1);_,after=section.split(END,1);return before+BEGIN+'\n'.join(lines)+'\n'+END+after
    assert PALETTE in text,'Keep the material generator trailing section intact'
    before,after=text.split(PALETTE,1)
    return before.rstrip()+'\n\n'+BEGIN+'\n'.join(lines)+'\n'+END+'\n'+PALETTE+after

def generate(check=False,definitions_only=False):
    data=payload();encoded=json.dumps(data,separators=(',',':'))+'\n'
    if check:assert DATA.read_text()==encoded,'Stale generated utility map meshes'
    else:DATA.parent.mkdir(parents=True,exist_ok=True);DATA.write_text(encoded)
    names=('data_chip_programmer','square_coupler','vehicle_coupler_slab','redstone_transmitter_block',*TRACKS)
    models=[f'customblock:id=%{name},state=*,class=com.vandorlabs.dynmap.UtilityModelRenderer' for name in names]
    textures=[f'texture:id=utility26_{i},filename=assets/vandorlabs/textures/{tex}.png,xcount=1,ycount=1' for i,tex in enumerate(data['textures'])]
    mapping=''.join(f',patch{i}=0:utility26_{i}' for i in range(len(data['textures'])))
    textures += [f'block:id=%{name},state=*'+mapping+',transparency=TRANSPARENT' for name in names]
    if not definitions_only:
        for filename,lines in [('dynmap-models.txt',models),('dynmap-texture.txt',textures)]:
            path=ASSETS/filename;content=definitions(path.read_text(),lines)
            if check:assert path.read_text()==content,'Stale utility Dynmap definitions '+filename
            else:path.write_text(content)
    print(f'Utility Dynmap meshes PASS states={len(data["states"])} textures={len(data["textures"])} programmer-parts=16 coupler-endpoints=36 native-track-defaults=12 transmitter-facings=6')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');parser.add_argument('--data-only',action='store_true');args=parser.parse_args();generate(args.check,args.data_only)
