#!/usr/bin/env python3
"""Reuse the standalone Data Chip Mount model at native scale on navigation items.

World housings omit the holder because their TESR uses the same baked standalone
model, including its contents. Run after importing the authored navigation pack.
"""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/vandorlabs'

def generate():
    native=json.loads((ASSETS/'models/block/token_slots/data_chip_slot_north_0.json').read_text())
    for kind,top in [('flat',14/16),('angled',8/16)]:
        folder=ASSETS/'models/block/navigation_table'/kind
        mtl=folder/'materials.mtl'
        text=mtl.read_text().split('# Native Data Chip Mount')[0].rstrip()+'\n'
        text+='# Native Data Chip Mount\n'
        for key,value in native['textures'].items():
            if key!='particle':text+=f'newmtl mount_{key}\nKa 1 1 1\nKd 1 1 1\nKs 0 0 0\nd 1\nillum 1\nmap_Kd {value}\n'
        mtl.write_text(text)
        for target in folder.glob('*_chip.obj'):
            source=target.with_name(target.name.replace('_chip',''))
            text=source.read_text().rstrip()+'\n# Native Data Chip Mount: 6 x 6 pixels\n'
            count=sum(line.startswith('v ') for line in text.splitlines());uvcount=sum(line.startswith('vt ') for line in text.splitlines());normcount=sum(line.startswith('vn ') for line in text.splitlines())
            cy=top-3/16-.5/16
            for element in native['elements']:
                a=[x/16 for x in element['from']];b=[x/16 for x in element['to']]
                x0,y0,z0=a;x1,y1,z1=b
                faces={'north':([(x1,y0,z0),(x0,y0,z0),(x0,y1,z0),(x1,y1,z0)],(0,0,-1)),
                       'south':([(x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1)],(0,0,1)),
                       'west':([(x0,y0,z0),(x0,y0,z1),(x0,y1,z1),(x0,y1,z0)],(-1,0,0)),
                       'east':([(x1,y0,z1),(x1,y0,z0),(x1,y1,z0),(x1,y1,z1)],(1,0,0)),
                       'up':([(x0,y1,z1),(x1,y1,z1),(x1,y1,z0),(x0,y1,z0)],(0,1,0)),
                       'down':([(x0,y0,z0),(x1,y0,z0),(x1,y0,z1),(x0,y0,z1)],(0,-1,0))}
                for face,description in element['faces'].items():
                    points,normal=faces[face]
                    text+='usemtl mount_'+description['texture'].lstrip('#')+'\n'
                    for x,y,z in points:text+=f'v {x:.10f} {y-.5+cy:.10f} {z-.975-.02625:.10f}\n'
                    # Native textures are square; preserve physical face UV density.
                    for x,y,z in points:
                        u,v=(x,1-y) if face in ('north','south') else (z,1-y) if face in ('east','west') else (x,z)
                        text+=f'vt {u:.10f} {1-v:.10f}\n'
                    text+='vn '+' '.join(map(str,normal))+'\n'
                    text+='f '+' '.join(f'{count+i}/{uvcount+i}/{normcount+1}' for i in range(1,5))+'\n'
                    count+=4;uvcount+=4;normcount+=1
            target.write_text(text)
        path=ASSETS/'blockstates'/f'navigation_table_{kind}.json'
        data=json.loads(path.read_text())
        for key,variants in data['variants'].items():
            if key.startswith('chip='):
                for variant in variants:variant['model']=variant['model'].replace('_chip.obj','.obj')
        path.write_text(json.dumps(data,indent=2)+'\n')
if __name__=='__main__':generate()
