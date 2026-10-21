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

def model(half,face,rotation,centered=False,height=None):
    h=height if height is not None else half
    inner=half-.6
    boxes=[(-half,-h,0,half,h,.35,'dark'),(-half,-h,.35,-inner,h,1.5,'metal'),(inner,-h,.35,half,h,1.5,'metal'),(-inner,-h,.35,inner,-h+.65,1.5,'metal')]
    elements=[]
    for a,b,c,d,e,f,texture in boxes:
        corners=[point(face,rotation,x,y,z) for x in (a,d) for y in (b,e) for z in (c,f)]
        if centered:corners=[[x,y,z-7.25] for x,y,z in corners]
        elements.append({'from':[min(p[i] for p in corners) for i in range(3)],'to':[max(p[i] for p in corners) for i in range(3)],'faces':{side:{'texture':'#'+texture} for side in NORMALS}})
    return {'textures':{'particle':'vandorlabs:blocks/programmable_glass/door_side','metal':'vandorlabs:blocks/programmable_glass/door_side','dark':'minecraft:blocks/concrete_black'},'elements':elements}

def main():
    for kind,half in [('card',3.0),('chip',3.0)]:
        name='data_'+kind+'_slot';variants={}
        for face in NORMALS:
            for rotation in range(4):
                authored=rotation if face in ('up','down') else 0
                path='token_slots/'+name+'_'+face+'_'+str(authored)
                write(ROOT/'models/block'/(path+'.json'),model(half,face,authored,height=4.4 if kind=='card' else 3.0))
                for on in ('false','true'):variants['facing='+face+',on='+on+',rotation='+str(rotation)]={'model':'vandorlabs:'+path}
        write(ROOT/'blockstates'/(name+'.json'),{'variants':variants})
        item=model(half,'north',0,True,height=4.4 if kind=='card' else 3.0)
        item['display']={'gui':{'rotation':[20,225,0],'scale':[.9,.9,.9]},'fixed':{'scale':[.8,.8,.8]},'ground':{'scale':[.5,.5,.5]}}
        write(ROOT/'models/item'/(name+'.json'),item)
    print('Generated padded card/chip mount icons and six-face slot frames')

if __name__=='__main__':main()
