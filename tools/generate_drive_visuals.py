#!/usr/bin/env python3
"""Separate authored drive emitter faces for warm Maxwell output and cyan Meridian pulsing."""
import argparse
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
MODELS=ROOT/'src/main/resources/assets/vandorlabs/models/block'

def generate(check=False):
    outputs={}
    for name,folder in [('maxwell','longburn_drive/longburn_drive'),('maxwell_off','longburn_drive/longburn_drive'),('meridian','dimension_drive/meridian_core')]:
        root=MODELS/folder/('' if name=='maxwell_off' else 'active');lines=(root/'static.obj').read_text().splitlines();vertices=[];normals=[];texcoords=[];prefix=[];body=[];lights=[];group='';material='atlas'
        for line in lines:
            parts=line.split()
            if not parts:continue
            if parts[0]=='v':vertices.append(tuple(map(float,parts[1:4])));prefix.append(line)
            elif parts[0]=='vt':texcoords.append(tuple(map(float,parts[1:3])));prefix.append(line)
            elif parts[0]=='vn':normals.append(tuple(map(float,parts[1:4])));prefix.append(line)
            elif parts[0]=='g':group=parts[1]
            elif parts[0]=='usemtl':material=parts[1]
            elif parts[0]=='f':
                corners=[tuple(map(int,word.split('/'))) for word in parts[1:]];normal=normals[corners[0][2]-1]
                selected=(name.startswith('maxwell') and group=='static__main_armored_chamber' and normal[2]>.99) or (name=='meridian' and group in ('static__west_phase_column','static__east_phase_column','static__central_phase_spine') and normal[2]<-.99)
                destination=lights if selected else body;destination.extend(['g '+group,'usemtl '+('drive_emitter' if selected and name.startswith('maxwell') else material)])
                if selected and name.startswith('maxwell'):
                    remapped=[]
                    for v,t,n in corners:
                        x,y,z=vertices[v-1];texcoords.append((x/3,y/3));prefix.append('vt %.10f %.10f'%texcoords[-1]);remapped.append(f'{v}/{len(texcoords)}/{n}')
                    line='f '+' '.join(remapped)
                destination.append(line)
        count=sum(line.startswith('f ') for line in lights);assert count==(1 if name.startswith('maxwell') else 3),(name,count)
        # All coordinate/UV/normal records precede faces; no face or holder geometry is discarded.
        common='# Generated authored drive split; geometry preserved, no gameplay propulsion.\nmtllib drive_materials.mtl\n'+'\n'.join(prefix)+'\n'
        outputs[root/'drive_body.obj']=common+'\n'.join(body)+'\n';outputs[root/'drive_lights.obj']=common+'\n'.join(lights)+'\n'
        material=(root/'materials.mtl').read_text()
        if name.startswith('maxwell'):material+='\nnewmtl drive_emitter\nKa 1 1 1\nKd 1 1 1\nd 1\nillum 1\nmap_Kd vandorlabs:blocks/thrusters/rocket_stream\n'
        outputs[root/'drive_materials.mtl']=material
        assert sum(line.startswith('f ') for line in body)+count==sum(line.startswith('f ') for line in lines)
    for path,content in outputs.items():
        if check:assert path.exists() and path.read_text()==content,'Stale drive visual split: '+str(path.relative_to(ROOT))
        else:path.write_text(content)
    print('Drive native visuals PASS Maxwell warm rocket artwork, Meridian3 cyan front faces; native geometry/UV preserved')
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');generate(parser.parse_args().check)
