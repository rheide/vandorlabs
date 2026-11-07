#!/usr/bin/env python3
"""Inactive ordinary Glass shader/atlas/backend focus; no canonical registration or world claim."""
import argparse,os
from pathlib import Path
from test_cube import ROOT,command,run,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--xvfb',action='store_true');args=parser.parse_args()
 if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='flat-glass-title-',dir=base));game=out/'client';game.mkdir();(game/'options.txt').write_text('onboardAccessibility:false\ntutorialStep:none\nrenderDistance:2\nmaxFps:60\nmipmapLevels:4\nguiScale:2\n')
 task=command('runClient','-PreaderRenderCheck','-PmodernClientRunDirectory='+str(game));env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_FLAT_GLASS_RENDER_CHECK='true')
 if args.xvfb:task=['xvfb-run','-a','-s','-screen 0 1280x720x24']+task;env['LIBGL_ALWAYS_SOFTWARE']='1'
 run(task,out/'client.log','[vandorlabs][flat-glass-render] title PASS',env)
 print('Inactive ordinary Glass title software PASS '+str(out.relative_to(ROOT)),flush=True)
if __name__=='__main__':main()
