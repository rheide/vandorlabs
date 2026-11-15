#!/usr/bin/env python3
"""Small native Surface topology and physical tile-staging lifecycle."""
import os
from pathlib import Path
from test_transmitter import ROOT, command, run, jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory, retain_artifacts


@retain_artifacts
def main():
    out = Path(artifact_directory(prefix='surface-runtime-', dir=ROOT / 'build/test-artifacts'))
    saved = out / 'saved-world'
    fixture_world(saved, 0)
    env = dict(os.environ, JAVA_HOME=str(jdk_home(21)), JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS', '') + ' -Dvandorlabs.turntables=true -Dvandorlabs.surfaceRuntimeChecks=true')
    run(command('runServer', '-PmodernServerRunDirectory=' + str(saved)), out / 'server.log', '[vandorlabs][surface-runtime] PASS', env)
    print('Native Surface root/hole/unequal repair and physical staging PASS ' + str(out), flush=True)


if __name__ == '__main__':
    main()
