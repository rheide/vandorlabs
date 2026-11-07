#!/usr/bin/env python3
"""Actual tray frame/content rendering against released GL capture, negative and reload."""
import argparse,os
from pathlib import Path
from test_cube import ROOT,command,run,jdk_home,check_log
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 p=argparse.ArgumentParser();p.add_argument('--xvfb',action='store_true');a=p.parse_args()
 if a.xvfb and os.name=='nt':p.error('--xvfb requires Linux')
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='screen-chip-tray-native-',dir=base));game=out/'client';game.mkdir();(game/'options.txt').write_text('onboardAccessibility:false\ntutorialStep:none\nguiScale:2\nmaxFps:60\n')
 launch=command('runClient','-PreaderRenderCheck','-PmodernClientRunDirectory='+str(game));env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_SCREEN_CHIP_TRAY_CHECK='true')
 if a.xvfb:launch=['xvfb-run','-a','-s','-screen 0 1280x720x24']+launch;env['LIBGL_ALWAYS_SOFTWARE']='1'
 run(launch,out/'client.log','[vandorlabs][screen-chip-tray] title PASS',env);check_log(out/'client.log','[vandorlabs][screen-chip-tray] reference PASS');check_log(out/'client.log','[vandorlabs][screen-chip-tray] warm PASS generation=1')
 from PIL import Image,ImageChops
 first=Image.open(game/'screenshots/tray-0-positive.png');second=Image.open(game/'screenshots/tray-1-positive.png')
 if ImageChops.difference(first,second).getbbox():raise AssertionError('Tray output changed across resource reload')
 print('Native Screen chip tray focused render PASS: '+str(out),flush=True)
if __name__=='__main__':main()
