#!/usr/bin/env python3
"""Map authored Core-machine OBJ faces through the existing clipped map surface renderer."""
import argparse,json,re,subprocess,tempfile,io
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/vandorlabs'
MARKER='# Generated Core machine maps\n'
END='# End Core machine maps\n'
PALETTE='# Generated tile-aware material palette\n'

def core_colors():
 # Dynmap has flat texture tiles rather than per-vertex RGB. Export the exact
 # portable model colors; each palette tile is one solid pixel, not authored art.
 from check_portable_boundary import jdk_home,executable
 home=jdk_home(8)
 with tempfile.TemporaryDirectory(prefix='vandor-core-palette-') as directory:
  subprocess.run([str(executable(home,'javac')),'-encoding','UTF-8','-d',directory,
                  '-sourcepath',str(ROOT/'src/portable/java'),str(ROOT/'tools/java/CorePaletteExport.java')],check=True)
  return json.loads(subprocess.check_output([str(executable(home,'java')),'-cp',directory,'CorePaletteExport'],text=True))

def generate(check=False):
 textures=[]
 def texture(name):
  assert name.startswith(('vandorlabs:','minecraft:')),name
  path=name.split(':',1)[1] if name.startswith('vandorlabs:') else name
  if path not in textures:textures.append(path)
  return textures.index(path)
 def obj(path):
  positions=[];uvs=[];faces=[];materials={};active=None
  material_file=next((line.split()[1] for line in path.read_text().splitlines() if line.startswith('mtllib ')),'materials.mtl')
  for line in (path.parent/material_file).read_text().splitlines():
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
 def state_obj(spec,path):
  root=ASSETS/'models/block'/spec['model']
  if spec['design']=='longburn_drive' and path=='static.obj':return obj(root/'drive_body.obj')+obj(root/'drive_lights.obj')
  if spec['design'] in ('longburn_drive','meridian_core') and path=='active/static.obj':return obj(root/'active/drive_body.obj')+obj(root/'active/drive_lights.obj')
  return obj(root/path)
 designs={}
 for spec in json.loads((ASSETS/'data/frame_machines.json').read_text()):
  paths=spec.get('state_models') or (['static.obj','active/static.obj'] if spec.get('chip_anchor') else ['complete.obj'])
  if spec.get('motion')=='shutters':paths=paths[:-1]+['active/complete_open.obj']
  designs[spec['block']]=dict(width=spec['width'],height=spec['height'],depth=spec['depth'],core=spec['design'],
                            states=[state_obj(spec,path) for path in paths])
 colors=core_colors()
 side=1
 while side*side<len(colors):side*=2
 palette=Image.new('RGBA',(side,side),(0,0,0,255))
 for index,color in enumerate(colors):palette.putpixel((index%side,index//side),(color>>16&255,color>>8&255,color&255,255))
 palette_path=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks/core_palette.png'
 encoded=io.BytesIO();palette.save(encoded,format='PNG',optimize=True)
 if check:
  with Image.open(palette_path) as old:assert old.size==palette.size and old.convert('RGBA').tobytes()==palette.tobytes(),'Stale flat Core color palette'
 else:palette_path.write_bytes(encoded.getvalue())
 data=dict(textures=textures,colors=colors,designs=designs)
 content=json.dumps(data,separators=(',',':'))+'\n';path=ASSETS/'data/dynmap_frame_models.json'
 if check:assert path.read_text()==content,'Stale Core map geometry'
 else:path.write_text(content)
 model_lines=[f'customblock:id=%{name},state=*,class=com.vandorlabs.dynmap.FrameMachineRenderer' for name in [*designs,'constructor_part']]
 texture_lines=[]
 for i,name in enumerate(textures):
  if name.startswith('minecraft:'):
   # Vanilla material sprites are supplied by Dynmap's Minecraft texture source.
   namespace,path='minecraft',name.split(':',1)[1];frames=1
  else:
   namespace,path='vandorlabs',name
   source=next(folder/(name+'.png') for folder in [ROOT/'texture-packs/additional/assets/vandorlabs/textures',ROOT/'texture-packs/default/assets/vandorlabs/textures'] if (folder/(name+'.png')).is_file())
   with Image.open(source) as image:
    frames=image.height//image.width if source.with_suffix('.png.mcmeta').exists() else 1
  texture_lines.append(f'texture:id=frame_machine_{i},filename=assets/{namespace}/textures/{path}.png,xcount=1,ycount={frames}')
 texture_lines.append(f'texture:id=frame_core_palette,filename=assets/vandorlabs/textures/blocks/core_palette.png,xcount={side},ycount={side}')
 patches=''.join(f',patch{i}=0:frame_machine_{i}' for i in range(len(textures)))
 patches+=''.join(f',patch{len(textures)+i}={i}:frame_core_palette' for i in range(len(colors)))
 texture_lines += [f'block:id=%{name},state=*'+patches+',transparency=TRANSPARENT' for name in [*designs,'constructor_part']]
 for filename,lines in [('dynmap-models.txt',model_lines),('dynmap-texture.txt',texture_lines)]:
  path=ASSETS/filename;s=path.read_text()
  records=MARKER+'\n'.join(lines)+'\n'+END
  if MARKER in s:
   before,section=s.split(MARKER,1);_,after=section.split(END,1)
   s=before+records+after
  else:
   assert PALETTE in s
   before,after=s.split(PALETTE,1)
   s=before.rstrip()+'\n\n'+records+'\n'+PALETTE+after
  if check:assert path.read_text()==s,'Stale Core map definitions '+filename
  else:path.write_text(s)
 print(f'Core machine Dynmap PASS {len(designs)} designs {len(textures)} native atlases; {len(colors)} shared flat Core colors; complete meshes clipped into reservations')
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');generate(parser.parse_args().check)
