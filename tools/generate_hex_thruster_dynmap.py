#!/usr/bin/env python3
"""Native UV-faithful solo and joined hexagonal propulsion map geometry."""
import argparse,json,re
from pathlib import Path
from generate_utility_dynmap import transformed
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/vandorlabs'
MODELS=ROOT/'generated-resources/assets/vandorlabs/models/block'
FAMILIES=('rocket_thruster','ion_drive','plasma_vent','impulse_engine')
IDS=tuple(f+'_hexagonal' for f in FAMILIES)
PARTS=('single','bottom_left','bottom_right','top_left','top_right')+tuple(f's3_x{x}_y{y}' for y in range(3) for x in range(3))
ROTATIONS={'north':(0,0),'east':(0,90),'south':(0,180),'west':(0,270),'up':(270,0),'down':(90,0)}
BEGIN='# Generated joined hexagonal propulsion models\n';END='# End joined hexagonal propulsion models\n'

def obj(path):
    points=[];uv=[];materials={};current=None;faces=[]
    for line in path.read_text().splitlines():
        p=line.split()
        if not p:continue
        if p[0]=='mtllib':
            name=None
            for entry in (path.parent/p[1]).read_text().splitlines():
                v=entry.split()
                if not v:continue
                if v[0]=='newmtl':name=v[1]
                elif v[0]=='map_Kd':materials[name]=v[1].split(':',1)[1]
        elif p[0]=='v':points.append(list(map(float,p[1:4])))
        elif p[0]=='vt':uv.append([float(p[1]),1-float(p[2])])
        elif p[0]=='usemtl':current=p[1]
        elif p[0]=='f':
            polygon=[]
            for token in p[1:]:
                a,b,*_=token.split('/');polygon.append(points[int(a)-1]+uv[int(b)-1])
            faces.append((materials[current],polygon))
    assert faces,path
    return faces

def generate(check=False):
    states={}
    for name in IDS:
        for mode in ('off','on','stream'):
            for part in PARTS:
                source=obj(MODELS/f'{name}_{mode}{"" if part=="single" else "_"+part}.obj')
                for facing,(x,y) in ROTATIONS.items():states[f'{name}/{facing}/{mode}/{part}']=transformed(source,x=x,y=y)
    textures=sorted({tex for faces in states.values() for tex,_ in faces});slots={tex:i for i,tex in enumerate(textures)}
    data={'textures':textures,'states':{key:[[slots[tex],*face] for tex,face in faces] for key,faces in sorted(states.items())}}
    path=ASSETS/'data/dynmap_hex_thrusters.json';encoded=json.dumps(data,separators=(',',':'))+'\n'
    if check:assert path.read_text()==encoded,'Stale hexagonal map geometry'
    else:path.write_text(encoded)
    models=[f'customblock:id=%{name},state=*,class=com.vandorlabs.dynmap.HexThrusterRenderer' for name in IDS]
    mappings=[f'texture:id=hexnative_{i},filename=assets/vandorlabs/textures/{tex}.png,xcount=1,ycount=1' for i,tex in enumerate(textures)]
    palette=''.join(f',patch{i}=0:hexnative_{i}' for i in range(len(textures)))
    mappings += [f'block:id=%{name},state=*'+palette+',transparency=TRANSPARENT' for name in IDS]
    for filename,lines in [('dynmap-models.txt',models),('dynmap-texture.txt',mappings)]:
        path=ASSETS/filename;text=path.read_text()
        if BEGIN in text:
            before,section=text.split(BEGIN,1);_,after=section.split(END,1);text=before+after
        text='\n'.join(line for line in text.splitlines() if not any(re.match(r'(?:modellist|customblock|block):id=%'+re.escape(name)+r',',line) for name in IDS))+'\n'
        marker='# Generated Core machine maps\n';before,after=text.split(marker,1)
        text=before.rstrip()+'\n\n'+BEGIN+'\n'.join(lines)+'\n'+END+'\n'+marker+after
        if check:assert path.read_text()==text,'Stale hexagonal map definitions '+filename
        else:path.write_text(text)
    print(f'Hexagonal Dynmap native meshes PASS states={len(states)} textures={len(textures)} solo + 2x2/3x3 native parts, six facings, three modes')
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');generate(parser.parse_args().check)
