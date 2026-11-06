#!/usr/bin/env python3
"""Actual public Door item placement compared with released original-click callbacks."""
import os
from pathlib import Path
from test_transmitter import ROOT, command, run, world, check_log, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts


@retain_artifacts
def main():
    reference = ROOT/'build/test-artifacts/door-placement-clicks-forge.json'
    if not reference.is_file():
        raise RuntimeError('exportDoorPlacementClickReference output required')
    output = Path(artifact_directory(prefix='door-placement-clicks-', dir=ROOT/'build/test-artifacts'))
    saved = output/'saved-world'
    world(saved, 0)
    run(command('runServer', '-PmodernServerRunDirectory='+str(saved), '-PtransmitterPhase=door-placement-clicks', '-PdoorPlacementClickReference='+str(reference)), output/'run.log', '[vandorlabs][door-placement-clicks] PASS', dict(os.environ, JAVA_HOME=str(jdk_home(21))))
    check_log(saved/'logs/latest.log', 'All dimensions are saved')
    print('Native Door original-click placement focused checks PASS: '+str(output), flush=True)


if __name__ == '__main__':
    main()
