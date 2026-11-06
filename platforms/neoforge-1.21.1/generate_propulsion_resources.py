#!/usr/bin/env python3
"""Project released engine JSON/OBJ geometry without substituting generic Light housings."""
import hashlib
import json
from pathlib import Path
import shutil
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
OUT=Path(__file__).resolve().parent/'build/generated/propulsion-resources'
ASSETS=OUT/'assets/vandorlabs'
SOURCES=[ROOT/p/'assets/vandorlabs' for p in ('generated-resources','src/main/resources')]

def source(kind,name):
    for base in SOURCES:
        p=base/kind/name
        if p.is_file():return p
    raise ValueError('Missing authored engine resource: '+kind+'/'+name)
def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,separators=(',',':'))+'\n')
def main():
    if OUT.exists():shutil.rmtree(OUT)
    models={};textures={};variants={};items={}
    def texture(name):
        if name.startswith('#') or not name.startswith('vandorlabs:'):return name
        path=name.split(':',1)[1]
        if name in textures:return name
        for base in (ROOT/'texture-packs/default/assets/vandorlabs',*SOURCES):
            p=base/'textures'/(path+'.png')
            if p.is_file():break
        else:raise ValueError('Missing standard engine sprite '+name)
        with Image.open(p) as image:
            width,height=image.size
            if width!=height or width>128 or width&(width-1):raise ValueError('Unexpected engine sprite dimensions '+name)
        target=ASSETS/'textures'/(path+'.png');target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,target)
        meta=p.with_suffix('.png.mcmeta')
        if meta.exists():shutil.copyfile(meta,target.with_suffix('.png.mcmeta'))
        textures[name]=dict(width=width,height=height,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
        return name
    def obj_faces(p):
        maps={}
        for line in p.read_text().splitlines():
            if line.startswith('mtllib '):
                material=None
                for entry in (p.parent/line.split(None,1)[1]).read_text().splitlines():
                    if entry.startswith('newmtl '):material=entry.split(None,1)[1]
                    if entry.startswith('map_Kd '):maps[material]=entry.split(None,1)[1]
                    if entry.startswith(('Kd ','d ')) and any(float(v)!=1 for v in entry.split()[1:]):raise ValueError('Unfrozen engine material color')
        points=[];normals=[];uvs=[];faces=[];material=None
        for line in p.read_text().splitlines():
            fields=line.split()
            if not fields:continue
            if fields[0]=='v':points.append([float(v) for v in fields[1:4]])
            elif fields[0]=='vn':normals.append([float(v) for v in fields[1:4]])
            elif fields[0]=='vt':uvs.append([float(v) for v in fields[1:3]])
            elif fields[0]=='usemtl':material=fields[1]
            elif fields[0]=='f':
                indices=[[int(v) for v in item.split('/')] for item in fields[1:]]
                if len(indices)==3:indices.append(indices[-1])
                if len(indices)!=4 or any(len(v)!=3 or min(v)<=0 for v in indices):raise ValueError('Unfrozen engine face shape')
                faces.append(dict(sprite=maps[material],position=[v for i in indices for v in points[i[0]-1]],normal=[v for i in indices for v in normals[i[2]-1]],uv=[v for i in indices for v in uvs[i[1]-1]]))
        return faces
    def model(name,inventory=False):
        if ':' not in name:name='minecraft:'+name
        namespace,path=name.split(':',1)
        if namespace!='vandorlabs':return name
        if path.startswith(('block/','item/')):kind,leaf=path.split('/',1)
        else:kind,leaf='block',path
        obj=leaf.endswith('.obj');leaf=leaf.removesuffix('.obj')
        native='vandorlabs:'+kind+'/propulsion/'+leaf
        if native in models:return native
        p=source('models',kind+'/'+leaf+('.obj' if obj else '.json'))
        models[native]=dict(source=name,kind='obj' if obj else 'json',sha256=hashlib.sha256(p.read_bytes()).hexdigest())
        if obj:
            models[native]["geometry"]=obj_faces(p)
            destination=ASSETS/'models'/kind/'propulsion'/(leaf+'.obj');destination.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,destination)
            for line in p.read_text().splitlines():
                if line.startswith('mtllib '):
                    material=line.split(None,1)[1];mtl=p.parent/material
                    if not mtl.is_file():raise ValueError('Missing authored engine material '+material)
                    target=destination.parent/material;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(mtl,target)
                    for entry in mtl.read_text().splitlines():
                        if entry.startswith('map_Kd '):texture(entry.split(None,1)[1])
            value={'loader':'neoforge:obj','model':'vandorlabs:models/'+kind+'/propulsion/'+leaf+'.obj','automatic_culling':False,'shade_quads':True,'flip_v':True,'emissive_ambient':False,'textures':{'particle':texture('vandorlabs:blocks/thrusters/side')}}
            if inventory:value['parent']='minecraft:block/block'
        else:
            value=json.loads(p.read_text())
            if 'parent' in value:value['parent']=model(value['parent'])
            value['textures']={key:texture(v) for key,v in value.get('textures',{}).items()}
        write(ASSETS/'models'/kind/'propulsion'/(leaf+'.json'),value)
        return native
    blocks=[b for b in json.loads(source('data','blocks.json').read_text()) if 'PropulsionLight' in b.get('class','')]
    if len(blocks)!=27:raise ValueError('Released engine block coverage')
    for block in blocks:
        name=block['id'];state=json.loads(source('blockstates',name+'.json').read_text());entries={}
        for key,value in state['variants'].items():
            if key=='inventory':
                if isinstance(value,list):value=value[0]
                items[name]={'model':model(value['model'],True),'x':value.get('x',0),'y':value.get('y',0),'transform':value.get('transform')}
                continue
            if isinstance(value,list):raise ValueError('Unexpected random engine variant')
            fields=dict(v.split('=',1) for v in key.split(','))
            if fields.get('particles')=='true' and fields.get('powered')=='false':continue
            mode=0 if fields['powered']=='false' else 2 if fields['particles']=='true' else 1
            normalized=fields['facing']+'/'+fields.get('part','single')+'/'+str(mode)
            entries[normalized]={'model':model(value['model']),'x':value.get('x',0),'y':value.get('y',0),'uvlock':value.get('uvlock',False)}
        variants[name]=entries
        item=next((base/'models/item'/(name+'.json') for base in SOURCES if (base/'models/item'/(name+'.json')).is_file()),None)
        if item is not None:items[name]={'model':model('vandorlabs:item/'+name),'x':0,'y':0,'transform':None}
    write(ASSETS/'data/propulsion_artwork.json',dict(blocks=variants,items=items,models=models,textures=textures))
    print('Native propulsion resources PASS blocks='+str(len(variants))+' models='+str(len(models))+' variants='+str(sum(map(len,variants.values())))+' sprites='+str(len(textures))+' exactSource JSON/OBJ/MTL geometry; no emissive-Ka or automatic-cull substitution')
if __name__=='__main__':main()
