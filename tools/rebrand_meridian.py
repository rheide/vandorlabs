"""Regenerate Meridian's native atlas with one existing Seyr nameplate.

The original model exporter is read from its artwork archive; only the runtime
OBJ/MTL and native-size atlas outputs are retained in the mod.
"""
import argparse,importlib.util,json,sys,tempfile
from pathlib import Path
from zipfile import ZipFile
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/vandorlabs'
TEX=ROOT/'texture-packs/additional/assets/vandorlabs/textures'

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('archive',type=Path);args=p.parse_args()
 with tempfile.TemporaryDirectory() as temp:
  temp=Path(temp)
  with ZipFile(args.archive) as z:
   wanted=['tools/build_models.py','tools/detail_textures.py','sources/meridian_core.json']
   for suffix in wanted:
    name=next(n for n in z.namelist() if n.endswith('/'+suffix));target=temp/suffix;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(z.read(name))
  source=temp/'sources/meridian_core.json';definition=json.loads(source.read_text())
  definition['title']='Seyr Meridian Drive';definition['brand']={'name':'Seyr'}
  definition['patches']=[patch for patch in definition['patches'] if patch['material']!='logo' or patch['name']=='front_cap_emblem']
  source.write_text(json.dumps(definition))
  sys.path.insert(0,str(temp/'tools'))
  spec=importlib.util.spec_from_file_location('drive_export',temp/'tools/build_models.py');export=importlib.util.module_from_spec(spec);spec.loader.exec_module(export)
  original=export.patch_texture
  logo=Image.open(TEX/'blocks/corp/seyr_logo.png').convert('RGBA')
  def patch_texture(patch,w,h,model_id,state):
   if patch['material']!='logo':return original(patch,w,h,model_id,state)
   # The artwork remains square, centered on its wider authored nameplate.
   tile=Image.new('RGBA',(w,h),export.palette(model_id)['dark_steel']);mark=logo.copy();mark.thumbnail((h,h),Image.Resampling.NEAREST);tile.alpha_composite(mark,((w-mark.width)//2,(h-mark.height)//2));return tile
  export.patch_texture=patch_texture
  export.build_one(source,temp/'out')
  runtime=temp/'out/src/main/resources/assets/mctrek'
  for path in runtime.rglob('*'):
   if not path.is_file():continue
   rel=path.relative_to(runtime)
   if rel.parts[0]=='blockstates' or 'parts' in rel.parts:continue
   out=TEX/Path(*rel.parts[1:]) if rel.parts[0]=='textures' else ASSETS/rel
   out.parent.mkdir(parents=True,exist_ok=True);data=path.read_bytes()
   if path.suffix in ('.obj','.mtl'):data=data.replace(b'mctrek:',b'vandorlabs:')
   out.write_bytes(data)
 print('Seyr Meridian runtime models regenerated: one front logo, four retired logos removed')
if __name__=='__main__':main()
