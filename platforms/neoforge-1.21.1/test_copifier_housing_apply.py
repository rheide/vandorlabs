#!/usr/bin/env python3
"""Real canonical housing setters and pre-mutation authority; no tool registration."""
import os
from pathlib import Path
from test_transmitter import ROOT, command, run, world, check_log, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    out = Path(artifact_directory(prefix='copifier-housing-apply-', dir=ROOT/'build/test-artifacts'))
    saved = out/'saved-world'
    world(saved, 0)
    reference = ROOT/'testclient/fixtures/copifier-housing-source.json.gz'
    for phase in ['write', 'read']:
        marker = '[vandorlabs][copifier-housing-apply] '+('read PASS' if phase == 'read' else 'PASS')
        run(command('runServer', '-PmodernServerRunDirectory='+str(saved), '-PtransmitterPhase=copifier-housing-apply-'+phase, '-PcopifierHousingReference='+str(reference)), out/(phase+'.log'), marker, dict(os.environ, JAVA_HOME=str(jdk_home(21))))
        check_log(saved/'logs/latest.log', 'All dimensions are saved')
    print('Native installed housing apply PASS: '+str(out), flush=True)

if __name__ == '__main__':
    main()
