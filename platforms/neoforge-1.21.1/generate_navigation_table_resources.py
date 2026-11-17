#!/usr/bin/env python3
"""Native table shells, shared single cassette artwork and inventory padding."""
import json,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
OUT=Path(__file__).resolve().parent/'build/generated/navigation-table-resources'
SRC=ROOT/'src/main/resources/assets/vandorlabs'
def main():
 if OUT.exists():shutil.rmtree(OUT)
 assets=OUT/'assets/vandorlabs'
 def write(path,data):
  path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,indent=2)+'\n')
 def texture(ref):
  ref=ref.replace('minecraft:blocks/concrete_black','minecraft:block/black_concrete')
  if ref == 'vandorlabs:items/data_chip_navigation':return ref  # credential resource owner
  if ref.startswith('vandorlabs:'):
   rel=ref.split(':',1)[1]+'.png'
   candidates=[ROOT/'texture-packs/additional/assets/vandorlabs/textures'/rel,ROOT/'texture-packs/default/assets/vandorlabs/textures'/rel,SRC/'textures'/rel]
   source=next((p for p in candidates if p.is_file()),None)
   if source is None:raise ValueError(ref)
   owners=list(OUT.parent.glob('*/assets/vandorlabs/textures/'+rel))
   if not any(p.parent!=OUT and p.read_bytes()==source.read_bytes() for p in owners):
    dest=assets/'textures'/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,dest)
  return ref
 for kind in ('flat','angled'):
  src=SRC/'models/block/navigation_table'/kind;dst=assets/'models/block/navigation_table'/kind;dst.mkdir(parents=True,exist_ok=True)
  lines=[]
  for line in (src/'materials.mtl').read_text().splitlines():
   if line.startswith('map_Kd '):line='map_Kd '+texture(line.split(None,1)[1])
   lines.append(line)
  (dst/'materials.mtl').write_text('\n'.join(lines)+'\n')
  variants={}
  for mask in range(16):
   actual=mask if kind=='flat' else mask&10
   obj=f'mask_{actual:02}.obj';shutil.copyfile(src/obj,dst/obj)
   write(dst/f'mask_{mask:02}.json',{'loader':'neoforge:obj','model':f'vandorlabs:models/block/navigation_table/{kind}/{obj}','automatic_culling':False,'shade_quads':True,'flip_v':True,'textures':{'particle':texture('vandorlabs:blocks/navigation_table/navigation_tables')}})
   for face,angle in [('north',0),('east',90),('south',180),('west',270)]:variants[f'connections={mask},facing={face}']={'model':f'vandorlabs:block/navigation_table/{kind}/mask_{mask:02}','y':angle}
  name='navigation_table_'+kind
  write(assets/f'blockstates/{name}.json',{'variants':variants})
  write(assets/f'models/item/{name}.json',{'parent':f'vandorlabs:block/navigation_table/{kind}/mask_00','display':{'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.46,.46,.46]},'ground':{'scale':[.25,.25,.25]},'fixed':{'scale':[.5,.5,.5]}}})
  write(OUT/f'data/vandorlabs/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'vandorlabs:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
  write(OUT/f'data/vandorlabs/recipe/{name}.json',{'type':'minecraft:crafting_shaped','category':'redstone','pattern':['GRG','MMM',' M '] if kind=='flat' else [' GR','GMM',' M '],'key':{'G':{'item':'minecraft:glass'},'R':{'item':'minecraft:redstone'},'M':{'item':'vandorlabs:programmable_matter_ingot'}},'result':{'id':'vandorlabs:'+name,'count':1}})
 for source,name in [('navigation_bay_frame','bay_frame'),('navigation_bay_module','bay_module')]:
  model=json.loads((SRC/f'models/block/module_rack/{source}.json').read_text());model['textures']={key:texture(value) for key,value in model['textures'].items()};write(assets/f'models/block/navigation_table/{name}.json',model)
 # The reader frame retains the table's authored physical chip holder without duplicating the shell.
 lines=(SRC/'models/block/navigation_table/angled/mask_00_chip.obj').read_text().splitlines();result=[];active=False
 for line in lines:
  if line.startswith('usemtl '):active=line.split()[1].startswith('mount_')
  if line.startswith(('v ','vt ','vn ','mtllib ')) or active and line.startswith(('usemtl ','f ')):result.append(line)
 dest=assets/'models/block/navigation_table';(dest/'chip_frame.obj').write_text('\n'.join(result)+'\n');(dest/'materials.mtl').write_text((dest/'angled/materials.mtl').read_text())
 write(dest/'chip_frame.json',{'loader':'neoforge:obj','model':'vandorlabs:models/block/navigation_table/chip_frame.obj','automatic_culling':False,'flip_v':True})
 print('Native navigation tables: 32 joined shells, authored reader and single cassette bay')
if __name__=='__main__':main()
