#!/usr/bin/env python3
"""Stopped native Ski extension and its exact saved-world reversal."""
import os
from pathlib import Path
from test_transmitter import ROOT, command, run, jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    out = Path(artifact_directory(prefix='gear-stowed-feet-', dir=ROOT / 'build/test-artifacts'))
    saved = out / 'saved-world'
    fixture_world(saved, 0)
    for phase in ('write', 'read'):
        env = dict(os.environ, JAVA_HOME=str(jdk_home(21)), JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS', '') + ' -Dvandorlabs.gearStowedFootPhase=' + phase)
        run(command('runServer', '-PmodernServerRunDirectory=' + str(saved)), out / (phase + '.log'), '[vandorlabs][gear-stowed-foot] ' + phase + ' PASS', env)
    print('Native stopped Ski append PASS ' + str(out), flush=True)

if __name__ == '__main__':
    main()
