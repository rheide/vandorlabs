#!/usr/bin/env python3
"""Inactive authored Programmer casing bake/reload, separate from player/world acceptance."""
import argparse,os
from pathlib import Path
from test_cube import ROOT,command,run,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--xvfb',action='store_true');args=parser.parse_args()
 if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
 output=Path(artifact_directory(prefix='programmer-bake-',dir=ROOT/'build/test-artifacts'));game=output/'client';game.mkdir();(game/'options.txt').write_text('onboardAccessibility:false\ntutorialStep:none\nrenderDistance:2\nmaxFps:60\nguiScale:2\n')
 launch=command('runClient','-PmodernClientRunDirectory='+str(game));env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.programmerRenderCheck=true')
 if args.xvfb:launch=['xvfb-run','-a','-s','-screen 0 1280x720x24']+launch;env['LIBGL_ALWAYS_SOFTWARE']='1'
 run(launch,output/'client.log','[vandorlabs][programmer-bake] title PASS',env)
 for generation in range(2):
  for kind in ('disabled','positive'):
   if not (game/'screenshots'/f'programmer-casing-{generation}-{kind}.png').is_file():raise AssertionError('Missing Programmer casing capture')
 print('Native Programmer title casing PASS: '+str(output),flush=True)
if __name__=='__main__':main()
