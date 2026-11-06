#!/usr/bin/env python3
"""Title-only actual Segmented Door widgets, picker interaction, resize and resource reload."""
import argparse,os
from pathlib import Path
from test_cube import ROOT,command,run,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 p=argparse.ArgumentParser();p.add_argument('--xvfb',action='store_true');a=p.parse_args()
 if a.xvfb and os.name=='nt':p.error('--xvfb requires Linux')
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='segmented-screen-title-',dir=base));game=out/'client';game.mkdir();(game/'options.txt').write_text('onboardAccessibility:false\ntutorialStep:none\nrenderDistance:2\nmaxFps:60\nguiScale:2\n')
 launch=command('runClient','-PmodernClientRunDirectory='+str(game));env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_SEGMENTED_SCREEN_CHECK='true')
 if a.xvfb:launch=['xvfb-run','-a','-s','-screen 0 1280x720x24']+launch;env['LIBGL_ALWAYS_SOFTWARE']='1'
 run(launch,out/'client.log','[vandorlabs][segmented-screen] title PASS',env)
 for name in ['segmented-door-page.png','segmented-glass-page.png','segmented-redstone-page.png','segmented-screen-reloaded.png']:
  if not (game/'screenshots'/name).is_file():raise AssertionError('Missing widget capture '+name)
 print('Native Segmented screen focused title check PASS: '+str(out),flush=True)
if __name__=='__main__':main()
