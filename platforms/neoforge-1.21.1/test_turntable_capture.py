#!/usr/bin/env python3
"""Check the read-only Turntable preflight on a disposable dedicated world."""
import os
from pathlib import Path

from native_test_artifacts import artifact_directory, retain_artifacts
from test_sampled_materials import fixture_world
from test_transmitter import ROOT, command, jdk_home, run


@retain_artifacts
def main():
    base = ROOT / 'build/test-artifacts'
    base.mkdir(parents=True, exist_ok=True)
    out = Path(artifact_directory(prefix='turntable-capture-', dir=base))
    saved = out / 'saved-world'
    fixture_world(saved, 0)
    env = dict(os.environ, JAVA_HOME=str(jdk_home(21)))
    env['JAVA_TOOL_OPTIONS'] = env.get('JAVA_TOOL_OPTIONS', '') + ' -Dvandorlabs.turntableCaptureChecks=true'
    run(command('runServer', '-PmodernServerRunDirectory=' + str(saved)), out / 'server.log',
        '[vandorlabs][turntable-capture] PASS', env)


if __name__ == '__main__':
    main()
