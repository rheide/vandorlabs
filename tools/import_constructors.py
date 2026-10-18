"""Import supplied Universal Constructor meshes and their client model cache."""
import argparse,json
from pathlib import Path
from zipfile import ZipFile
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[1];ASSETS=ROOT/'src/main/resources/assets/vandorlabs';TEX=ROOT/'texture-packs/additional/assets/vandorlabs/textures'
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('archive',type=Path);p.add_argument('--assets-only',action='store_true',help='Preserve integrated Java renderers, core icons and blockstates');args=p.parse_args()
 with ZipFile(args.archive) as z:
  for name in z.namelist():
   if '/resources/assets/mctrek/' in name:
    rel=name.split('/resources/assets/mctrek/',1)[1]
    if rel.startswith(('models/','textures/')) and name.endswith(('.obj','.mtl','.png')):
     target=TEX/rel[len('textures/'):] if rel.startswith('textures/') else ASSETS/rel
     target.parent.mkdir(parents=True,exist_ok=True);data=z.read(name)
     if name.endswith(('.obj','.mtl')):data=data.replace(b'mctrek:',b'vandorlabs:')
     target.write_bytes(data)
   if not args.assets_only and (name.endswith('UniversalConstructorModelCache.java') or name.endswith('UniversalConstructorFastTESR.java')):
    target=ROOT/'src/main/java/com/vandorlabs/client/constructors'/Path(name).name;target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(z.read(name).decode().replace('package mctrek.client.model;','package com.vandorlabs.client.constructors;').replace('"mctrek"','"vandorlabs"'))
 if args.assets_only:return
 for index,id in enumerate(['assembly_press','synthesis_tower','enclosed_gantry']):
  tex=TEX/'blocks/matter'/(id+'_core.png');tex.parent.mkdir(parents=True,exist_ok=True)
  image=Image.new('RGB',(64,64),(39,48,60));d=ImageDraw.Draw(image);color=[(82,207,222),(168,115,228),(233,168,74)][index]
  d.rectangle((2,2,61,61),outline=(135,151,163),width=3);d.rectangle((10,10,53,53),outline=color,width=3)
  for n in range(index+1):d.rectangle((20+n*9,19,24+n*9,44),fill=color)
  image.save(tex,optimize=True)
  def write(rel,data):
   dest=ASSETS/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(json.dumps(data,indent=2)+'\n')
  write('models/block/'+id+'_core.json',{'parent':'block/cube_all','textures':{'all':'vandorlabs:blocks/matter/'+id+'_core'}})
  write('models/item/'+id+'_core.json',{'parent':'vandorlabs:block/'+id+'_core','display':{'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.5,.5,.5]}}})
  write('blockstates/'+id+'_core.json',{'variants':{'facing='+f+',formed='+str(formed).lower():{'model':'vandorlabs:'+('constructor_empty' if formed else id+'_core')} for f in ['north','east','south','west'] for formed in [False,True]}})
 write('models/block/constructor_empty.json',{'parent':'block/block','textures':{'particle':'vandorlabs:blocks/matter/casing'}})
 write('blockstates/constructor_part.json',{'variants':{'normal':{'model':'vandorlabs:constructor_empty'}}})
if __name__=='__main__':main()
