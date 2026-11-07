#!/usr/bin/env python3
"""Actual ordinary Glass ItemRenderer: all304 Source variants, padding, tiny pack override and reload."""
import argparse,os,json,hashlib
from pathlib import Path
from test_cube import ROOT,command,run,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts

@retain_artifacts
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--xvfb',action='store_true');parser.add_argument('--canonical',action='store_true');args=parser.parse_args()
 if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='flat-glass-icons-',dir=base));game=out/'client';game.mkdir();(game/'options.txt').write_text('onboardAccessibility:false\ntutorialStep:none\nrenderDistance:2\nmaxFps:60\nguiScale:1\n')
 generated=ROOT/'platforms/neoforge-1.21.1/build/generated/flat-glass-resources';relative=Path('assets/vandorlabs/models/item/flat-glass-authored/configured/glass_regular_0_clear_t0_f0.json');source=generated/relative
 if not source.is_file():raise RuntimeError('generateFlatGlassResources output required')
 pack=game/'resourcepacks/glass-item-tiny';target=pack/relative;target.parent.mkdir(parents=True);(pack/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':34,'description':'Glass Source geometry preservation fixture'}}));body=json.loads(source.read_text());body['elements'][0]['from'][0]+=0.00001;target.write_text(json.dumps(body))
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_FLAT_GLASS_ITEM_CHECK='true',VANDOR_FLAT_GLASS_CANONICAL_CHECK=str(args.canonical).lower());task=command('runClient','-PreaderRenderCheck','-PmodernClientRunDirectory='+str(game))
 if args.xvfb:task=['xvfb-run','-a','-s','-screen 0 1280x720x24']+task;env['LIBGL_ALWAYS_SOFTWARE']='1'
 run(task,out/'client.log','[vandorlabs][glass-item-model] title PASS',env)
 from PIL import Image
 reports=[]
 for generation in [0,1]:
  image=Image.open(game/'screenshots'/('glass-items-'+str(generation)+'.png')).convert('RGB');icons=[]
  for i in range(304):
   x=24+i%16*36;y=40+i//16*32
   visible=[(dx,dy) for dy in range(-1,17) for dx in range(-1,17) if image.getpixel((x+dx,y+dy))!=(0,0,0)]
   if any(dx<1 or dx>14 or dy<1 or dy>14 for dx,dy in visible):raise AssertionError('Actual projected icon padding escapes slot variant='+str(i)+' generation='+str(generation))
   icons.append({'index':i,'visible_pixels':len(visible)})
  if sum(v['visible_pixels'] for v in icons)<1000:raise AssertionError('Actual ItemRenderer gallery empty')
  reports.append({'generation':generation,'icons':icons})
 (out/'icon-padding-report.json').write_text(json.dumps(reports,indent=2));classes=ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge';files=[classes/'NativeGlassItemModel.class',classes/'glass/NativeGlassItem.class'];(out/'artifact.sha256').write_text('\n'.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name for p in files)+'\n')
 print('Ordinary Glass actual item software PASS all304/padding/reload '+str(out.relative_to(ROOT)),flush=True)
if __name__=='__main__':main()
