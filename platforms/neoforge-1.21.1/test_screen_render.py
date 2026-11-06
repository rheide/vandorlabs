#!/usr/bin/env python3
"""Seven released Screen roles through the inactive native compiler; isolated title client."""
import argparse
import os
from pathlib import Path
from test_cube import ROOT,command,run,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts

@retain_artifacts
def main():
 p=argparse.ArgumentParser();p.add_argument('--xvfb',action='store_true');a=p.parse_args()
 if a.xvfb and os.name=='nt':p.error('--xvfb requires Linux')
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='screen-render-native-',dir=base));game=out/'client';game.mkdir();(game/'options.txt').write_text('onboardAccessibility:false\ntutorialStep:none\nrenderDistance:2\nmaxFps:60\nguiScale:2\n')
 launch=command('runClient','-PreaderRenderCheck','-PmodernClientRunDirectory='+str(game));env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_SCREEN_RENDER_CHECK='true')
 if a.xvfb:launch=['xvfb-run','-a','-s','-screen 0 1280x720x24']+launch;env['LIBGL_ALWAYS_SOFTWARE']='1'
 run(launch,out/'client.log','[vandorlabs][screen-render] title PASS',env)
 if not all((game/'screenshots'/name).is_file() for name in ('screen-render-disabled.png','screen-render-positive.png')):raise AssertionError('Missing actual Screen role capture')
 print('Focused native Screen render PASS: '+str(out),flush=True)

if __name__=='__main__':main()
