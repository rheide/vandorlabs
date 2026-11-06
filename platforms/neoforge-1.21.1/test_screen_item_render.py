#!/usr/bin/env python3
"""Title-only Screen inventory rendering through the inactive native backend."""
import argparse
import os
from pathlib import Path
from PIL import Image, ImageChops
from test_cube import ROOT, command, run, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts


@retain_artifacts
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--xvfb', action='store_true')
    args = parser.parse_args()
    if args.xvfb and os.name == 'nt':
        parser.error('--xvfb requires Linux')
    base = ROOT / 'build/test-artifacts'
    base.mkdir(parents=True, exist_ok=True)
    output = Path(artifact_directory(prefix='screen-item-render-', dir=base))
    game = output / 'client'
    game.mkdir()
    (game / 'options.txt').write_text('onboardAccessibility:false\ntutorialStep:none\nrenderDistance:2\nmaxFps:60\nmipmapLevels:4\nguiScale:2\n')
    launch = command('runClient', '-PreaderRenderCheck', '-PmodernClientRunDirectory=' + str(game))
    env = dict(os.environ, JAVA_HOME=str(jdk_home(21)), VANDOR_SCREEN_ITEM_RENDER_CHECK='true')
    if args.xvfb:
        launch = ['xvfb-run', '-a', '-s', '-screen 0 1280x720x24'] + launch
        env['LIBGL_ALWAYS_SOFTWARE'] = '1'
    run(launch, output / 'client.log', '[vandorlabs][screen-item-render] title PASS', env)
    before = Image.open(game / 'screenshots/screen-items-0.png').convert('RGB')
    after = Image.open(game / 'screenshots/screen-items-1.png').convert('RGB')
    if before.size != after.size or ImageChops.difference(before, after).getbbox():
        raise AssertionError('Screen inventory changed after resource reload')
    scale = before.width / 640
    def icon(scene):
        x, y = 30 + scene % 4 * 155, 35 + scene // 4 * 165
        return before.crop(tuple(round(value * scale) for value in (x, y, x + 100, y + 100)))
    changed = sum(pixel != (0, 0, 0) for pixel in ImageChops.difference(icon(5), icon(7)).getdata())
    if changed < 200:
        raise AssertionError('Diagonal control-panel variant is not visible: ' + str(changed))
    print('Screen inventory image PASS reload pixels unchanged; diagonal controls changed=' + str(changed), flush=True)
    print('Screen inventory title-only development/software check: ' + str(output.relative_to(ROOT)), flush=True)


if __name__ == '__main__':
    main()
