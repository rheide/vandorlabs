#!/usr/bin/env python3
"""Canonical Screen fluid eligibility, real buckets/flow and private ownership across restart."""
import os
from pathlib import Path
from test_transmitter import ROOT, command, run, world, check_log, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    out = Path(artifact_directory(prefix='screen-fluid-protection-', dir=ROOT/'build/test-artifacts'))
    saved = out/'saved-world'
    world(saved, 0)
    for phase in ['write', 'read']:
        run(command('runServer', '-PmodernServerRunDirectory='+str(saved), '-PtransmitterPhase=screen-fluid-protection-'+phase), out/(phase+'.log'), '[vandorlabs][screen-fluid-protection] '+phase+' PASS', dict(os.environ, JAVA_HOME=str(jdk_home(21))))
        check_log(saved/'logs/latest.log', 'All dimensions are saved')
    print('Canonical Screen fluid protection PASS: '+str(out), flush=True)

if __name__ == '__main__':
    main()
