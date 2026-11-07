#!/usr/bin/env python3
"""Inactive authored Programmer casing bake/reload, separate from player/world acceptance."""
import argparse,os,json,re,subprocess
from pathlib import Path
from test_cube import ROOT,command,run,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--xvfb',action='store_true');args=parser.parse_args()
 if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
 # Fail before creating a runtime if incremental compilation omitted the moved smoke class.
 subprocess.run(command('compileSmokeJava'),cwd=Path(__file__).resolve().parent,env=dict(os.environ,JAVA_HOME=str(jdk_home(21))),check=True)
 fixture=Path(__file__).resolve().parent/'build/classes/java/smoke/com/vandorlabs/neoforge/ProgrammerBakeChecks.class'
 if not fixture.is_file():raise AssertionError('Programmer smoke class missing after compileSmokeJava; rebuild smoke Java outputs before launching a client')
 output=Path(artifact_directory(prefix='programmer-bake-',dir=ROOT/'build/test-artifacts'));game=output/'client';game.mkdir();(game/'options.txt').write_text('onboardAccessibility:false\ntutorialStep:none\nrenderDistance:2\nmaxFps:60\nguiScale:2\n')
 pack=game/'resourcepacks/programmer-replacement';assets=pack/'assets/vandorlabs/models/block/data_chip_programmer/ready/parts';assets.mkdir(parents=True)
 (pack/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':34,'description':'Programmer authored replacement fixture'}}))
 faces={side:{'texture':'#all'} for side in ('down','up','north','south','west','east')}
 (assets/'static.json').write_text(json.dumps({'textures':{'all':'minecraft:block/gold_block'},'elements':[{'from':[2,1,2],'to':[8,6,8],'faces':faces}]}))
 original=(ROOT/'src/main/resources/assets/vandorlabs/models/block/data_chip_programmer/ready/parts/screen.obj').read_text()
 changed=re.sub(r'(?m)^v ([^ ]+)',lambda m:'v '+str(float(m[1])+0.000001),original,count=1)
 assert changed!=original
 (assets/'screen.obj').write_text(changed)
 launch=command('runClient','-PmodernClientRunDirectory='+str(game));env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.programmerRenderCheck=true')
 if args.xvfb:launch=['xvfb-run','-a','-s','-screen 0 1280x720x24']+launch;env['LIBGL_ALWAYS_SOFTWARE']='1'
 run(launch,output/'client.log','[vandorlabs][programmer-bake] title PASS',env)
 for generation in range(4):
  for kind in ('disabled','positive'):
   if not (game/'screenshots'/f'programmer-casing-{generation}-{kind}.png').is_file():raise AssertionError('Missing Programmer casing capture')
 print('Native Programmer title casing PASS: '+str(output),flush=True)
if __name__=='__main__':main()
