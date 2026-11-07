#!/usr/bin/env python3
"""Settings-only native undo after actual item operations."""
import os
from pathlib import Path
from test_transmitter import ROOT, command, run, world, check_log, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    out = Path(artifact_directory(prefix='copifier-undo-', dir=ROOT/'build/test-artifacts'))
    saved = out/'saved-world'
    world(saved, 0)
    reference = ROOT/'testclient/fixtures/copifier-housing-source.json.gz'
    run(command('runServer', '-PmodernServerRunDirectory='+str(saved), '-PtransmitterPhase=copifier-undo', '-PcopifierHousingReference='+str(reference)), out/'server.log', '[vandorlabs][copifier-undo] PASS', dict(os.environ, JAVA_HOME=str(jdk_home(21))))
    print('Native settings undo PASS: '+str(out), flush=True)

if __name__ == '__main__':
    main()
