#!/usr/bin/env python3
"""Project the two chipless navigation observers using their released artwork."""
import argparse,hashlib,json,shutil,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
PLATFORM=Path(__file__).resolve().parent
SOURCE=ROOT/'src/main/resources/assets/vandorlabs'
OUT=PLATFORM/'build/generated/navigation-observer-resources'
IDS=('standalone_dish','standalone_antenna')
def generate(out):
 assets=out/'assets/vandorlabs'; textures={}
 def write(path,value):
  path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n')
 def texture(reference):
  reference=reference.replace('minecraft:blocks/','minecraft:block/')
  if not reference.startswith('vandorlabs:') or reference in textures:return reference
  relative=Path('textures')/(reference.split(':',1)[1]+'.png')
  source=next((base/relative for base in (ROOT/'texture-packs/additional/assets/vandorlabs',ROOT/'texture-packs/default/assets/vandorlabs',SOURCE) if (base/relative).is_file()),None)
  if source is None:raise ValueError('Missing observer artwork '+reference)
  textures[reference]=hashlib.sha256(source.read_bytes()).hexdigest()
  owners=[p for p in OUT.parent.iterdir() if p.is_dir() and p!=OUT]+[PLATFORM/'src/main/resources']
  target=assets/relative
  duplicate=next((owner/'assets/vandorlabs'/relative for owner in owners if (owner/'assets/vandorlabs'/relative).is_file()),None)
  if duplicate is not None:
   if duplicate.read_bytes()!=source.read_bytes():raise ValueError('Observer sprite differs from native owner '+reference)
  else:
   target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
   meta=source.with_suffix('.png.mcmeta')
   if meta.exists():shutil.copyfile(meta,target.with_suffix('.png.mcmeta'))
  return reference
 texture('vandorlabs:blocks/core_flat')
 for design in IDS:
  source=SOURCE/'models/block/radar'/design;dest=assets/'models/block/navigation_observers'/design
  paths=('scanning/parts/static','scanning/parts/rotor') if design=='standalone_dish' else ('scanning/complete',)
  for path in paths:
   target=dest/path;target.parent.mkdir(parents=True,exist_ok=True);target.with_suffix('.obj').write_bytes((source/(path+'.obj')).read_bytes())
   material=source/Path(path).parent/'materials.mtl';lines=[]
   for line in material.read_text().splitlines():
    if line.startswith('map_Kd '):line='map_Kd '+texture(line.split(None,1)[1])
    lines.append(line)
   (target.parent/'materials.mtl').write_text('\n'.join(lines)+'\n')
   write(target.with_suffix('.json'),{'loader':'neoforge:obj','model':f'vandorlabs:models/block/navigation_observers/{design}/{path}.obj','automatic_culling':False,'shade_quads':True,'flip_v':True,'emissive_ambient':False})
  core=json.loads((SOURCE/f'models/block/{design}_core.json').read_text());core['textures']={k:texture(v) for k,v in core['textures'].items()};write(dest/'core.json',core)
  item=json.loads((SOURCE/f'models/item/{design}_core.json').read_text());item['parent']=f'vandorlabs:block/navigation_observers/{design}/core';write(assets/f'models/item/{design}_core.json',item)
  write(assets/f'blockstates/{design}_core.json',{'variants':{f'facing={face},formed={str(formed).lower()}':{'model':'vandorlabs:block/navigation_observers/empty' if formed else f'vandorlabs:block/navigation_observers/{design}/core','y':yaw} for face,yaw in (('north',0),('east',90),('south',180),('west',270)) for formed in (False,True)}})
  write(out/f'data/vandorlabs/loot_table/blocks/{design}_core.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'vandorlabs:{design}_core'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
 write(assets/'models/block/navigation_observers/empty.json',{'parent':'minecraft:block/block'})
 write(assets/'blockstates/observer_part.json',{'variants':{'':{'model':'vandorlabs:block/navigation_observers/empty'}}})
 write(assets/'data/native_observers.json',{'textures':textures})
def contents(folder):return {str(p.relative_to(folder)):p.read_bytes() for p in folder.rglob('*') if p.is_file()}
def main():
 check=argparse.ArgumentParser();check.add_argument('--check',action='store_true');args=check.parse_args()
 with tempfile.TemporaryDirectory() as temp:
  generated=Path(temp);generate(generated)
  if args.check:
   if contents(OUT)!=contents(generated):raise SystemExit('Observer resources are stale')
  else:
   if OUT.exists():shutil.rmtree(OUT)
   shutil.copytree(generated,OUT)
 print('Native observers: two chipless structures; original sprites; padded native core icons')
if __name__=='__main__':main()
