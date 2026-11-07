#!/usr/bin/env python3
"""Actual atlas mixed Tile rendering versus closed terrain through two resource generations."""
import argparse,os
from pathlib import Path
from test_cube import ROOT,command,run,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--xvfb',action='store_true');args=parser.parse_args()
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='segmented-tile-render-',dir=base));game=out/'client';game.mkdir();(game/'options.txt').write_text('onboardAccessibility:false\ntutorialStep:none\nrenderDistance:2\nmaxFps:60\nmipmapLevels:4\nguiScale:2\n')
 launch=command('runClient','-PreaderRenderCheck','-PmodernClientRunDirectory='+str(game));env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_SEGMENTED_TILE_CHECK='true')
 if args.xvfb:launch=['xvfb-run','-a','-s','-screen 0 1280x720x24']+launch;env['LIBGL_ALWAYS_SOFTWARE']='1'
 run(launch,out/'client.log','[vandorlabs][segmented-tile] title PASS',env);print('Native Segmented Tile rendering PASS: '+str(out),flush=True)
if __name__=='__main__':main()
