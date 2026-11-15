#!/usr/bin/env python3
"""One abrupt source-only cross-chunk save at the real durable ARMED fault point."""
import os
from pathlib import Path
from test_transmitter import ROOT, command, run, jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    out = Path(artifact_directory(prefix='cell-recovery-', dir=ROOT / 'build/test-artifacts'))
    saved = out / 'saved-world'
    fixture_world(saved, 0)
    for phase in ('crash', 'read'):
        env = dict(os.environ, JAVA_HOME=str(jdk_home(21)), JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS', '') + ' -Dvandorlabs.cellJournalChecks=true -Dvandorlabs.cellJournalPhase=' + phase)
        log = out / (phase + '.log')
        run(command('runServer', '-PmodernServerRunDirectory=' + str(saved)), log, '[vandorlabs][cell-journal] ' + phase + ' PASS', env)
        if phase == 'crash':
            text = log.read_text(errors='replace')
            marker = text.index('sourceOnlyCrossChunkSave/zeroLooseItems/abruptHalt')
            if 'Stopping server' in text[marker:]:
                raise AssertionError('Abrupt writer unexpectedly ran graceful server shutdown')
    print('Typed cell transfer abrupt source-only recovery PASS ' + str(out), flush=True)

if __name__ == '__main__':
    main()
