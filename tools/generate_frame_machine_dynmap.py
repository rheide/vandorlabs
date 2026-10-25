#!/usr/bin/env python3
"""Map authored Core-machine OBJ faces through the existing clipped map surface renderer."""
import argparse,json,re
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/vandorlabs'
MARKER='# Generated Core machine maps\n'
END='# End Core machine maps\n'
PALETTE='# Generated tile-aware material palette\n'

def generate(check=False):
 textures=[]
 def texture(name):
  assert name.startswith('vandorlabs:'),name
  path=name.split(':',1)[1]
  if path not in textures:textures.append(path)
  return textures.index(path)
 def obj(path):
  positions=[];uvs=[];faces=[];materials={};active=None
  for line in (path.parent/'materials.mtl').read_text().splitlines():
   parts=line.split()
   if not parts:continue
   if parts[0]=='newmtl':active=parts[1]
   elif parts[0]=='map_Kd':materials[active]=texture(parts[1])
  active=None
  for line in path.read_text().splitlines():
   parts=line.split()
   if not parts:continue
   if parts[0]=='v':positions.append(list(map(float,parts[1:4])))
   elif parts[0]=='vt':uvs.append(list(map(float,parts[1:3])))
   elif parts[0]=='usemtl':active=parts[1]
   elif parts[0]=='f':
    vertices=[]
    for value in parts[1:]:
     p,t,*_=value.split('/');u,v=uvs[int(t)-1];vertices.append(positions[int(p)-1]+[u,1-v])
    assert len(vertices)==4
    faces.append(dict(texture=materials[active],vertices=vertices))
  assert faces,path
  return faces
 designs={}
 for spec in json.loads((ASSETS/'data/frame_machines.json').read_text()):
  paths=spec.get('state_models') or (['static.obj','active/static.obj'] if spec.get('chip_anchor') else ['complete.obj'])
  if spec.get('motion')=='shutters':paths=paths[:-1]+['active/complete_open.obj']
  designs[spec['block']]=dict(width=spec['width'],height=spec['height'],depth=spec['depth'],seed=texture('vandorlabs:blocks/matter/'+spec['block']),
                            states=[obj(ASSETS/'models/block'/spec['model']/path) for path in paths])
 data=dict(textures=textures,designs=designs)
 content=json.dumps(data,separators=(',',':'))+'\n';path=ASSETS/'data/dynmap_frame_models.json'
 if check:assert path.read_text()==content,'Stale Core map geometry'
 else:path.write_text(content)
 model_lines=[f'customblock:id=%{name},state=*,class=com.vandorlabs.dynmap.FrameMachineRenderer' for name in [*designs,'constructor_part']]
 texture_lines=[]
 for i,name in enumerate(textures):
  source=next(folder/(name+'.png') for folder in [ROOT/'texture-packs/additional/assets/vandorlabs/textures',ROOT/'texture-packs/default/assets/vandorlabs/textures'] if (folder/(name+'.png')).is_file())
  with Image.open(source) as image:
   frames=image.height//image.width if source.with_suffix('.png.mcmeta').exists() else 1
  texture_lines.append(f'texture:id=frame_machine_{i},filename=assets/vandorlabs/textures/{name}.png,xcount=1,ycount={frames}')
 patches=''.join(f',patch{i}=0:frame_machine_{i}' for i in range(len(textures)))
 texture_lines += [f'block:id=%{name},state=*'+patches+',transparency=TRANSPARENT' for name in [*designs,'constructor_part']]
 for filename,lines in [('dynmap-models.txt',model_lines),('dynmap-texture.txt',texture_lines)]:
  path=ASSETS/filename;s=path.read_text();s=re.sub(re.escape(MARKER)+'.*?'+re.escape(END),'',s,flags=re.S)
  records=MARKER+'\n'.join(lines)+'\n'+END
  assert PALETTE in s
  before,after=s.split(PALETTE,1)
  s=before.rstrip()+'\n\n'+records+'\n'+PALETTE+after
  if check:assert path.read_text()==s,'Stale Core map definitions '+filename
  else:path.write_text(s)
 print(f'Core machine Dynmap PASS {len(designs)} designs {len(textures)} native atlases/seed textures; complete authored meshes clipped into reservations')
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');generate(parser.parse_args().check)
