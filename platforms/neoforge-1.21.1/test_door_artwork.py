#!/usr/bin/env python3
"""Title-only authored door model/sprite/mipmap binding; no world or server."""
import argparse
import os
from pathlib import Path
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
    output = Path(artifact_directory(prefix='door-artwork-title-', dir=base))
    game = output / 'client'
    game.mkdir()
    (game / 'options.txt').write_text('onboardAccessibility:false\ntutorialStep:none\nrenderDistance:2\nmaxFps:60\nmipmapLevels:4\n')
    launch = command('runClient', '-PdoorArtworkReference=' + str(ROOT / 'build/test-artifacts/door-artwork-forge.jsonl'), '-PmodernClientRunDirectory=' + str(game))
    env = dict(os.environ, JAVA_HOME=str(jdk_home(21)), VANDOR_DOOR_RESOURCE_CHECK='true')
    if args.xvfb:
        launch = ['xvfb-run', '-a', '-s', '-screen 0 1280x720x24'] + launch
        env['LIBGL_ALWAYS_SOFTWARE'] = '1'
    run(launch, output / 'client.log', '[vandorlabs][door-artwork] title PASS', env)
    print('Exact authored door title-only development/software check: ' + str(output.relative_to(ROOT)), flush=True)


if __name__ == '__main__':
    main()
