#!/usr/bin/env python3
"""Project released ordinary-glass authored rails/items; reuse existing full-content Door and glass atlas sources."""
import hashlib,json,shutil,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
OUT=Path(__file__).resolve().parent/'build/generated/flat-glass-resources'
ASSETS=OUT/'assets/vandorlabs'
BASES=[ROOT/p/'assets/vandorlabs' for p in ('generated-resources','src/main/resources')]
def source(kind,path):
 for base in BASES:
  candidate=base/kind/path
  if candidate.is_file():return candidate
 raise ValueError('Missing released glass resource '+kind+'/'+path)
def write(path,data):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,separators=(',',':'))+'\n')
def main():
 if OUT.exists():shutil.rmtree(OUT)
 doors=json.loads((OUT.parent/'door-resources/assets/vandorlabs/data/door_artwork.json').read_text())
 door_textures={value['original']:value['id'] for value in doors['textures']}
 sys.path.insert(0,str(ROOT/'tools'))
 from compact_runtime_assets import redirect,texture_aliases
 standard_aliases=texture_aliases(json.loads(source('data','unified_textures.json').read_text()))
 models={};aliases={}
 def texture(name):
  name=redirect(name,standard_aliases)
  if name.startswith('#') or name.startswith('minecraft:'):return name
  if name in aliases:return aliases[name]
  if name in door_textures:mapped=door_textures[name]
  elif name=='vandorlabs:blocks/programmable_glass/metal_side':mapped='vandorlabs:frame-default/metal_side'
  elif name.startswith(('vandorlabs:blocks/programmable_glass/low/','vandorlabs:blocks/programmable_glass/medium/')):mapped='vandorlabs:glass-palette/'+name.rsplit('/',1)[1]
  else:raise ValueError('Unmapped released glass texture '+name)
  aliases[name]=mapped;return mapped
 def model(name):
  name=redirect(name,standard_aliases)
  if name.startswith('minecraft:'):return name
  path=name.split(':',1)[1]
  if not path.startswith(('block/','item/')):path='block/'+path
  kind,leaf=path.split('/',1);native='vandorlabs:'+kind+'/flat-glass-authored/'+leaf
  if native in models:return native
  file=source('models',path+'.json');models[native]={'source':'vandorlabs:'+path,'sha256':hashlib.sha256(file.read_bytes()).hexdigest()}
  data=redirect(json.loads(file.read_text()),standard_aliases)
  if 'parent' in data:
   parent=data['parent']
   if ':' not in parent:parent='minecraft:'+parent
   data['parent']=model(parent)
  if 'textures' in data:data['textures']={key:texture(value) for key,value in data['textures'].items()}
  write(ASSETS/'models'/kind/'flat-glass-authored'/(leaf+'.json'),data)
  return native
 state=redirect(json.loads(source('blockstates','programmable_glass.json').read_text()),standard_aliases);rules=[]
 for part in state['multipart']:
  apply=part['apply'];rules.append({'when':part['when'],'model':model(apply['model']),'y':apply.get('y',0)})
 items=[]
 glass_source=(ROOT/'src/portable/java/com/vandorlabs/tiles/GlassTextures.java').read_text()
 import re
 shades=re.findall(r'"([a-z_]+)"',re.search(r'IDS=\{(.*?)\}',glass_source).group(1))
 for size in range(2):
  for shade in shades:
   for tint in range(4):
    for frame in range(2):items.append(model(f'vandorlabs:item/configured/glass_regular_{size}_{shade}_t{tint}_f{frame}'))
 write(ASSETS/'data/flat_glass_artwork.json',{'models':models,'textures':aliases,'rules':rules,'items':items})
 print('Native flat glass resources PASS models='+str(len(models))+' itemVariants='+str(len(items))+' multipartRules='+str(len(rules))+' existing full-content Door/frame/palette sources')
if __name__=='__main__':main()
