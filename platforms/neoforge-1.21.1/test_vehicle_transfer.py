#!/usr/bin/env python3
"""Exercise native vehicle cell/actor ownership, including abrupt cold recovery."""
import os
from pathlib import Path

from native_test_artifacts import artifact_directory, retain_artifacts
from test_sampled_materials import fixture_world
from test_transmitter import ROOT, command, jdk_home, run


@retain_artifacts
def main():
    output = Path(artifact_directory(prefix='vehicle-transfer-', dir=ROOT / 'build/test-artifacts'))
    opts = os.environ.get('JAVA_TOOL_OPTIONS', '')
    for phases in (('roundtrip',), ('crash-assemble', 'read-assemble'), ('crash-park', 'read-park')):
        world = output / phases[0] / 'saved-world'
        fixture_world(world, 0)
        for phase in phases:
            log = output / (phase + '.log')
            label = '[vandorlabs][vehicle-transfer] ' + phase + ' PASS'
            env = dict(os.environ, JAVA_HOME=str(jdk_home(21)), JAVA_TOOL_OPTIONS=opts
                       + ' -Dvandorlabs.cellJournalFaults=true -Dvandorlabs.vehicleTransferPhase=' + phase)
            run(command('runServer', '-PmodernServerRunDirectory=' + str(world),
                        '-x', 'compileJava', '-x', 'compileSmokeJava', '-x', 'processResources'), log, label, env)
            if phase.startswith('crash-'):
                text = log.read_text(errors='replace')
                if 'Stopping server' in text[text.index(label):]:
                    raise AssertionError('Fault writer reached graceful shutdown: ' + str(log))
    print('Native vehicle transfer and two abrupt cold recoveries passed. ' + str(output), flush=True)


if __name__ == '__main__':
    main()
