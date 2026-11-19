#!/usr/bin/env python3
"""Exercise actual asynchronous Turntable capture, restore, and cold partial-save recovery."""
import os
from pathlib import Path

from native_test_artifacts import artifact_directory, retain_artifacts
from test_sampled_materials import fixture_world
from test_transmitter import ROOT, command, jdk_home, run


@retain_artifacts
def main():
    out = Path(artifact_directory(prefix='turntable-transfer-', dir=ROOT / 'build/test-artifacts'))
    for phase in ('normal', 'crash', 'read'):
        saved = out / ('normal-world' if phase == 'normal' else 'recovery-world')
        if phase != 'read':
            fixture_world(saved, 0)
        env = dict(os.environ, JAVA_HOME=str(jdk_home(21)))
        env['JAVA_TOOL_OPTIONS'] = (env.get('JAVA_TOOL_OPTIONS', '')
                                    + ' -Dvandorlabs.turntableControllers=true'
                                    + ' -Dvandorlabs.cellJournalFaults=true'
                                    + ' -Dvandorlabs.turntableTransferChecks=' + phase)
        log = out / (phase + '.log')
        marker = '[vandorlabs][turntable-transfer] ' + phase + ' PASS'
        run(command('runServer', '-PmodernServerRunDirectory=' + str(saved)), log, marker, env)
        if phase == 'crash' and 'Stopping server' in log.read_text(errors='replace').split(marker, 1)[1]:
            raise AssertionError('Abrupt writer ran graceful shutdown')


if __name__ == '__main__':
    main()
