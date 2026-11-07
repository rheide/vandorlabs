#!/usr/bin/env python3
"""Inactive complete-OBJ Programmer item: actual GPU padding/reload and warm immutable submission."""
import argparse,os,subprocess,json
from pathlib import Path
from PIL import Image,ImageChops
from test_cube import ROOT,command,run,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--xvfb',action='store_true');args=parser.parse_args()
 if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
 platform=Path(__file__).resolve().parent;env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.programmerRenderCheck=true -Dvandorlabs.programmerItemCheck=true')
 subprocess.run(command('compileSmokeJava'),cwd=platform,env=env,check=True)
 if not (platform/'build/classes/java/smoke/com/vandorlabs/neoforge/ProgrammerItemRenderChecks.class').is_file():raise AssertionError('Missing compiled Programmer item fixture')
 out=Path(artifact_directory(prefix='programmer-item-',dir=ROOT/'build/test-artifacts'));game=out/'client';game.mkdir();(game/'options.txt').write_text('onboardAccessibility:false\ntutorialStep:none\nguiScale:2\nmaxFps:60\nrenderDistance:2\n')
 pack=game/'resourcepacks/programmer-item-replacement';models=pack/'assets/vandorlabs/models/block/data_chip_programmer/ready';models.mkdir(parents=True)
 (pack/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':34,'description':'Complete Programmer item replacement fixture'}}))
 (models/'complete.json').write_text(json.dumps({'textures':{'all':'minecraft:block/gold_block','particle':'minecraft:block/gold_block'},'elements':[{'from':[0,0,0],'to':[16,16,16],'faces':{side:{'texture':'#all'} for side in ('down','up','north','south','west','east')}}]}))
 launch=command('runClient','-PmodernClientRunDirectory='+str(game))
 if args.xvfb:launch=['xvfb-run','-a','-s','-screen 0 1280x720x24']+launch;env['LIBGL_ALWAYS_SOFTWARE']='1'
 run(launch,out/'client.log','[vandorlabs][programmer-item-render] title PASS',env)
 for generation in range(4):
  for mode in ('positive','disabled'):
   if not (game/f'screenshots/programmer-item-{generation}-{mode}.png').is_file():raise AssertionError('Missing padded item generation capture')
 baseline=Image.open(game/'screenshots/programmer-item-0-positive.png').convert('RGB')
 for generation in (1,3):
  if ImageChops.difference(baseline,Image.open(game/f'screenshots/programmer-item-{generation}-positive.png').convert('RGB')).getbbox():raise AssertionError('Item reload/restore changed authored pixels')
 if ImageChops.difference(baseline,Image.open(game/'screenshots/programmer-item-2-positive.png').convert('RGB')).getbbox() is None:raise AssertionError('Replacement item model did not reach GPU')
 print('Native Programmer item title PASS: '+str(out),flush=True)
if __name__=='__main__':main()
