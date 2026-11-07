#!/usr/bin/env python3
"""All native vanilla material states, saved identities and collision refusal."""
import os
from pathlib import Path
from test_transmitter import ROOT, command, run, world, check_log, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    out = Path(artifact_directory(prefix='explicit-material-', dir=ROOT/'build/test-artifacts'))
    saved = out/'saved-world'
    world(saved, 0)
    reference = ROOT/'testclient/fixtures/copifier-housing-source.json.gz'
    run(command('runServer', '-PmodernServerRunDirectory='+str(saved), '-PtransmitterPhase=explicit-material', '-PcopifierHousingReference='+str(reference)), out/'server.log', '[vandorlabs][explicit-material] PASS', dict(os.environ, JAVA_HOME=str(jdk_home(21))))
    print('Native explicit material states PASS: '+str(out), flush=True)

if __name__ == '__main__':
    main()
