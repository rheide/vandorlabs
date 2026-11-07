#!/usr/bin/env python3
"""Project the released Programmer OBJ/MTL identities; no replacement box geometry."""
import hashlib,json,shutil
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
OUT=Path(__file__).resolve().parent/'build/generated/programmer-resources'
SOURCE=ROOT/'src/main/resources/assets/vandorlabs'
def write(path,value):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,separators=(',',':'))+'\n')
def main():
 if OUT.exists():shutil.rmtree(OUT)
 assets=OUT/'assets/vandorlabs';models={};textures={}
 def texture(name):
  if name in textures:return
  if not name.startswith('vandorlabs:'):raise ValueError('Unfrozen Programmer namespace '+name)
  leaf=name.split(':',1)[1]+'.png'
  for base in (ROOT/'texture-packs/additional/assets/vandorlabs',ROOT/'texture-packs/default/assets/vandorlabs',SOURCE,ROOT/'generated-resources/assets/vandorlabs'):
   source=base/'textures'/leaf
   if source.is_file():break
  else:raise ValueError('Missing standard Programmer sprite '+name)
  with Image.open(source) as image:
   width,height=image.size
   if width>128 or width&(width-1) or height%width or (height!=width and not source.with_suffix('.png.mcmeta').is_file()):raise ValueError('Unexpected Programmer texture resolution '+name)
  target=assets/'textures'/leaf;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
  meta=source.with_suffix('.png.mcmeta')
  if meta.is_file():shutil.copyfile(meta,target.with_suffix('.png.mcmeta'))
  textures[name]=dict(width=width,height=height,sha256=hashlib.sha256(source.read_bytes()).hexdigest())
 def model(resource):
  name=resource.removesuffix('.obj');path=SOURCE/'models'/(resource.split(':',1)[1]);points=[];normals=[];uvs=[];faces=[];maps={};material=None
  target=assets/'models'/path.relative_to(SOURCE/'models');target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,target)
  for line in path.read_text().splitlines():
   fields=line.split()
   if not fields:continue
   if fields[0]=='mtllib':
    mtl=path.parent/fields[1];shutil.copyfile(mtl,target.parent/fields[1]);current=None
    for entry in mtl.read_text().splitlines():
     words=entry.split()
     if not words:continue
     if words[0]=='newmtl':current=words[1]
     elif words[0]=='map_Kd':maps[current]=words[1];texture(words[1])
     elif words[0] in ('Kd','d') and any(float(v)!=1 for v in words[1:]):raise ValueError('Unfrozen material color')
   elif fields[0]=='v':points.append([float(v) for v in fields[1:4]])
   elif fields[0]=='vn':normals.append([float(v) for v in fields[1:4]])
   elif fields[0]=='vt':uvs.append([float(v) for v in fields[1:3]])
   elif fields[0]=='usemtl':material=fields[1]
   elif fields[0]=='f':
    indices=[[int(v) for v in word.split('/')] for word in fields[1:]]
    if len(indices)==3:indices.append(indices[-1])
    if len(indices)!=4 or any(len(v)!=3 or min(v)<=0 for v in indices):raise ValueError('Unfrozen Programmer OBJ face')
    faces.append(dict(sprite=maps[material],position=[v for i in indices for v in points[i[0]-1]],normal=[v for i in indices for v in normals[i[2]-1]],uv=[v for i in indices for v in uvs[i[1]-1]]))
  write(assets/'models'/(name.split(':',1)[1]+'.json'),{'loader':'neoforge:obj','model':'vandorlabs:models/'+resource.split(':',1)[1],'automatic_culling':False,'shade_quads':True,'flip_v':True,'emissive_ambient':False,'textures':{'particle':'vandorlabs:blocks/data_chip_programmer/programmer_ready'}})
  models[name]=dict(source=resource,sha256=hashlib.sha256(path.read_bytes()).hexdigest(),geometry=faces)
  return name
 manifest=json.loads((SOURCE/'data_chip_programmer/data_chip_programmer.json').read_text());states={}
 for state,value in manifest['states'].items():states[state]={part:model(data['obj_resource']) for part,data in value['parts'].items()}
 item=model(manifest['states']['ready']['obj_resource'])
 if len(models)!=21 or len(states)!=4 or any(len(parts)!=5 for parts in states.values()):raise ValueError('Released Programmer coverage')
 write(assets/'data/programmer_artwork.json',dict(states=states,item=item,models=models,textures=textures))
 write(assets/'models/block/programmer_empty.json',{'elements':[],'textures':{'particle':'vandorlabs:blocks/data_chip_programmer/programmer_ready'}})
 # Canonical registration and item wrapping remain disabled; these assets carry no activation.
 print('Native Programmer resources PASS states=4 parts=20 item=1 sprites='+str(len(textures)))
if __name__=='__main__':main()
