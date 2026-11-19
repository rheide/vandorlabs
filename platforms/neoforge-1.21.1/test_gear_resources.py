#!/usr/bin/env python3
"""Run native arm transfer through aligned endpoints and a saved-process reload."""
import os
from pathlib import Path

from native_test_artifacts import artifact_directory, retain_artifacts
from test_transmitter import ROOT, command, jdk_home, run, world


@retain_artifacts
def main():
    base = ROOT/'build/test-artifacts'
    base.mkdir(parents=True, exist_ok=True)
    out = Path(artifact_directory(prefix='gear-resources-', dir=base))
    saved = out/'saved-world'
    world(saved, 0)
    for phase in ('roundtrip', 'reload'):
        env = dict(os.environ, JAVA_HOME=str(jdk_home(21)),
                   JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS', '') +
                   ' -Dvandorlabs.gearResourcePhase=' + phase)
        run(command('runServer', '-PmodernServerRunDirectory=' + str(saved)),
            out/(phase + '.log'), '[vandorlabs][gear-resources] ' + phase + ' PASS', env)
    print('Native arm resources PASS ' + str(out), flush=True)


if __name__ == '__main__':
    main()
