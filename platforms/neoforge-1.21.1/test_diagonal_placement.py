#!/usr/bin/env python3
"""Compare actual native click contexts to frozen released diagonal placement callbacks."""
import argparse, os
from pathlib import Path
from test_transmitter import ROOT, command, run, world, check_log, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts


@retain_artifacts
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--legacy-reference', type=Path, required=True)
    args = parser.parse_args()
    reference = args.legacy_reference.resolve()
    if not reference.is_file():
        parser.error('exportDiagonalPlacementReference output required')
    output = Path(artifact_directory(prefix='diagonal-placement-native-', dir=ROOT/'build/test-artifacts'))
    saved = output/'saved-world'
    world(saved, 0)
    run(command('runServer', '-PmodernServerRunDirectory='+str(saved), '-PtransmitterPhase=diagonal-placement', '-PdiagonalPlacementReference='+str(reference)), output/'write.log', '[vandorlabs][diagonal-placement] write PASS', dict(os.environ, JAVA_HOME=str(jdk_home(21))))
    check_log(saved/'logs/latest.log', 'All dimensions are saved')
    print('Native diagonal placement focused checks PASS: '+str(output), flush=True)


if __name__ == '__main__':
    main()
