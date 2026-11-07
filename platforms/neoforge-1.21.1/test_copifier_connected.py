#!/usr/bin/env python3
"""Bounded installed housing connected-region discovery before mutation."""
import os
from pathlib import Path
from test_transmitter import ROOT, command, run, world, check_log, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    out = Path(artifact_directory(prefix='copifier-connected-', dir=ROOT/'build/test-artifacts'))
    saved = out/'saved-world'
    world(saved, 0)
    reference = ROOT/'testclient/fixtures/copifier-connected-source.json.gz'
    run(command('runServer', '-PmodernServerRunDirectory='+str(saved), '-PtransmitterPhase=copifier-connected', '-PcopifierConnectedReference='+str(reference)), out/'server.log', '[vandorlabs][copifier-connected] world PASS', dict(os.environ, JAVA_HOME=str(jdk_home(21))))
    print('Native connected housing PASS: '+str(out), flush=True)

if __name__ == '__main__':
    main()
