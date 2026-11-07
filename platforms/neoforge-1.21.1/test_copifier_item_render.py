#!/usr/bin/env python3
"""Focused three-state icon GPU check, independent from public tool activation."""
import argparse
import os
from pathlib import Path
from PIL import Image, ImageChops
from test_cube import ROOT, command, run, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--xvfb', action='store_true')
    args = parser.parse_args()
    if args.xvfb and os.name == 'nt':
        parser.error('--xvfb requires Linux')
    out = Path(artifact_directory(prefix='copifier-item-render-', dir=ROOT/'build/test-artifacts'))
    game = out/'client'
    game.mkdir()
    (game/'options.txt').write_text('onboardAccessibility:false\ntutorialStep:none\nguiScale:2\nmaxFps:60\nrenderDistance:2\n')
    env = dict(os.environ, JAVA_HOME=str(jdk_home(21)), JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS', '')+' -Dvandorlabs.copifierItemCheck=true')
    launch = command('runClient', '-PmodernClientRunDirectory='+str(game))
    if args.xvfb:
        launch = ['xvfb-run', '-a', '-s', '-screen 0 1280x720x24']+launch
        env['LIBGL_ALWAYS_SOFTWARE'] = '1'
    run(launch, out/'client.log', '[vandorlabs][copifier-item-render] title PASS', env)
    for mode in ('positive', 'disabled'):
        first = Image.open(game/f'screenshots/copifier-item-0-{mode}.png').convert('RGB')
        second = Image.open(game/f'screenshots/copifier-item-1-{mode}.png').convert('RGB')
        if ImageChops.difference(first, second).getbbox():
            raise AssertionError('Resource reload changed icon pixels: '+mode)
    print('Copied tool icon render PASS '+str(out), flush=True)

if __name__ == '__main__':
    main()
