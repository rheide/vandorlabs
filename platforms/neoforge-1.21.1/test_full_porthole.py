#!/usr/bin/env python3
"""Focused actual full-depth porthole registration plus independent saved-world restart."""
import argparse
import os
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
        parser.error('Actual Forge full porthole export required')
    base = ROOT / 'build/test-artifacts'
    base.mkdir(parents=True, exist_ok=True)
    output = Path(artifact_directory(prefix='full-porthole-native-', dir=base))
    saved = output / 'saved-world'
    world(saved, 0)
    for phase in ('full-porthole-write', 'full-porthole-read'):
        run(command('runServer', '-PmodernServerRunDirectory=' + str(saved),
                    '-PtransmitterPhase=' + phase, '-PfullPortholeReference=' + str(reference)),
            output / (phase + '.log'), '[vandorlabs][' + phase + '] dedicated PASS',
            dict(os.environ, JAVA_HOME=str(jdk_home(21))))
        check_log(saved / 'logs/latest.log', 'All dimensions are saved')
    print('Full-depth porthole focused registration/restart PASS: ' + str(output), flush=True)

if __name__ == '__main__':
    main()
