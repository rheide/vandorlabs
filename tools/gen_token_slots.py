#!/usr/bin/env python3
"""Compact card/chip slot frames for all six mount faces and floor rotations."""
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/vandorlabs'
NORMALS={'north':(0,0,-1),'south':(0,0,1),'east':(1,0,0),'west':(-1,0,0),'up':(0,1,0),'down':(0,-1,0)}
BASES={'north':((1,0,0),(0,1,0)),'south':((-1,0,0),(0,1,0)),'east':((0,0,1),(0,1,0)),'west':((0,0,-1),(0,1,0)),'up':((1,0,0),(0,0,1)),'down':((1,0,0),(0,0,-1))}

def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,separators=(',',':'))+'\n',encoding='utf-8')

def point(face,rotation,x,y,z):
    u,v=BASES[face]
    if face in ('up','down'):
        for _ in range(rotation):u,v=v,tuple(-a for a in u)
    return [8-NORMALS[face][i]*(8-z)+u[i]*x+v[i]*y for i in range(3)]

def model(half,face,rotation,centered=False):
    inner=half-1
    boxes=[(-half,-half,0,half,half,.4,'dark'),(-half,-half,.4,-inner,half,1.5,'metal'),(inner,-half,.4,half,half,1.5,'metal'),(-inner,-half,.4,inner,-inner,1.5,'metal'),(-inner,inner,.4,inner,half,1.5,'metal')]
    elements=[]
    for a,b,c,d,e,f,texture in boxes:
        corners=[point(face,rotation,x,y,z) for x in (a,d) for y in (b,e) for z in (c,f)]
        if centered:corners=[[x,y,z-7.25] for x,y,z in corners]
        elements.append({'from':[min(p[i] for p in corners) for i in range(3)],'to':[max(p[i] for p in corners) for i in range(3)],'faces':{side:{'texture':'#'+texture} for side in NORMALS}})
    return {'textures':{'particle':'minecraft:blocks/iron_block','metal':'minecraft:blocks/iron_block','dark':'minecraft:blocks/concrete_black'},'elements':elements}

def main():
    for kind,half in [('card',4.5),('chip',3.5)]:
        name='data_'+kind+'_slot';variants={}
        for face in NORMALS:
            for rotation in range(4):
                authored=rotation if face in ('up','down') else 0
                path='token_slots/'+name+'_'+face+'_'+str(authored)
                write(ROOT/'models/block'/(path+'.json'),model(half,face,authored))
                for on in ('false','true'):variants['facing='+face+',on='+on+',rotation='+str(rotation)]={'model':'vandorlabs:'+path}
        write(ROOT/'blockstates'/(name+'.json'),{'variants':variants})
        item=model(half,'north',0,True)
        item['display']={'gui':{'rotation':[20,225,0],'scale':[.9,.9,.9]},'fixed':{'scale':[.8,.8,.8]},'ground':{'scale':[.5,.5,.5]}}
        write(ROOT/'models/item'/(name+'.json'),item)
    print('Generated padded card/chip mount icons and six-face slot frames')

if __name__=='__main__':main()
