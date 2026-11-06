#!/usr/bin/env python3
"""Public Light registry, configured item, recipe, harvest and saved-owner acceptance."""
import os
from pathlib import Path
from test_transmitter import ROOT, command, run, world, check_log, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts


@retain_artifacts
def main():
    output = Path(artifact_directory(prefix='light-canonical-', dir=ROOT/'build/test-artifacts'))
    saved = output/'saved-world'
    world(saved, 0)
    for phase in ('write', 'read'):
        run(command('runServer', '-PmodernServerRunDirectory='+str(saved), '-PtransmitterPhase=light-canonical-'+phase), output/(phase+'.log'), '[vandorlabs][light-canonical] '+phase+' PASS', dict(os.environ, JAVA_HOME=str(jdk_home(21))))
        check_log(saved/'logs/latest.log', 'All dimensions are saved')
    print('Native canonical Light focused checks PASS: '+str(output), flush=True)


if __name__ == '__main__':
    main()
