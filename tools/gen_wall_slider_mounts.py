#!/usr/bin/env python3
"""Pose the canonical north wall slider and keep flat handles aligned with panel UVs."""
import copy,itertools,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/vandorlabs'
NORMAL={'west':(-1,0,0),'east':(1,0,0),'down':(0,-1,0),'up':(0,1,0),'north':(0,0,-1),'south':(0,0,1)}
NAMES={v:k for k,v in NORMAL.items()}
def yaw(v,turns):
    x,y,z=v
    for _ in range(turns):x,z=-z,x
    return x,y,z

def pose(data,transform):
    result=copy.deepcopy(data)
    for element in result['elements']:
        corners=[tuple(v+8 for v in transform(tuple(c-8 for c in point))) for point in itertools.product(*zip(element['from'],element['to']))]
        element['from']=[min(p[i] for p in corners) for i in range(3)]
        element['to']=[max(p[i] for p in corners) for i in range(3)]
        element['faces']={NAMES[transform(NORMAL[face])]:value for face,value in element['faces'].items()}
    return result

for detail in (32,):
    folder=ASSETS/f'models/block/thruster_controls/{detail}px'
    for state in ('off','low','medium','high'):
        data=json.loads((folder/f'wall_slider_{state}_north.json').read_text())
        for mount,turns in (('east',1),('south',2),('west',3)):
            target=pose(data,lambda v:yaw(v,turns))
            (folder/f'wall_slider_{state}_{mount}.json').write_text(json.dumps(target,indent=2)+'\n')
        for mount in ('floor_z','ceiling_z'):
            transform=(lambda v:(v[0],-v[2],v[1])) if mount=='floor_z' else (lambda v:(v[0],v[2],-v[1]))
            target=pose(data,transform)
            # The UP/DOWN panel UVs retain the accepted artwork orientation.
            # Move only the protruding grip/cap to the marked row on that image.
            for element in target['elements'][1:]:
                element['from'][2],element['to'][2]=16-element['to'][2],16-element['from'][2]
            (folder/f'wall_slider_{state}_{mount}.json').write_text(json.dumps(target,indent=2)+'\n')
        for mount in ('floor_x','ceiling_x'):
            (folder/f'wall_slider_{state}_{mount}.json').unlink(missing_ok=True)
    states=ASSETS/'blockstates/wall_slider.json'
    if not states.exists():continue
    blockstates=json.loads(states.read_text())
    for key,variant in blockstates['variants'].items():
        props=dict(p.split('=') for p in key.split(','));face=props['facing']
        if face in ('up','down'):
            mount='floor_z' if face=='up' else 'ceiling_z'
            state=('off','low','medium','high')[int(props['level'])]
            variant['model']=f'vandorlabs:thruster_controls/{detail}px/wall_slider_{state}_{mount}'
            rotation=int(props['rotation'])*90
            variant.pop('y',None)
            if rotation:variant['y']=rotation
    states.write_text(json.dumps(blockstates,indent=2)+'\n')
