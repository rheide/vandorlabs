#!/usr/bin/env python3
"""Render exactly three builders plus synthesis effects through the real native client pipeline."""
import argparse,os
from test_cube import ROOT,command,run,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
from navigation_test_artifact import snapshot
@retain_artifacts
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--xvfb',action='store_true');args=parser.parse_args()
    if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);output=artifact_directory(prefix='builder-render-title-',dir=base)
    from pathlib import Path
    output=Path(output);game=output/'client';game.mkdir();(game/'options.txt').write_text('onboardAccessibility:false\ntutorialStep:none\nrenderDistance:2\nmaxFps:60\nmipmapLevels:4\nguiScale:2\n')
    frozen=snapshot(output,'before')
    launch=command('runClient','-PbuilderRenderCheck','-PmodernClientRunDirectory='+str(game),'-x','compileJava','-x','compileSmokeJava','-x','processResources');env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_BUILDER_RENDER_CHECK='true')
    if args.xvfb:launch=['xvfb-run','-a','-s','-screen 0 1280x720x24']+launch;env['LIBGL_ALWAYS_SOFTWARE']='1'
    try:run(launch,output/'client.log','[vandorlabs][builder-render] title PASS',env)
    finally:snapshot(output,'after',frozen)
    print('Three-builder native title rendering development/software check: '+str(output.relative_to(ROOT)),flush=True)
if __name__=='__main__':main()
