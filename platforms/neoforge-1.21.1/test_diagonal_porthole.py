#!/usr/bin/env python3
"""Focused actual diagonal porthole registration plus independent saved-world restart."""
import argparse
import hashlib
import os
from pathlib import Path
from test_transmitter import ROOT, command, run, world, check_log, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--legacy-reference', type=Path, required=True)
    parser.add_argument('--glass-contract', action='store_true', help='Run only the small registered-state glass projection contracts')
    args = parser.parse_args()
    reference = args.legacy_reference.resolve()
    if not reference.is_file():
        parser.error('Actual Forge diagonal geometry export required')
    base = ROOT / 'build/test-artifacts'
    base.mkdir(parents=True, exist_ok=True)
    output = Path(artifact_directory(prefix='diagonal-porthole-native-', dir=base))
    digest = hashlib.sha256(reference.read_bytes()).hexdigest()
    (output / 'reference.sha256').write_text(digest + '  ' + reference.name + '\n', encoding='utf-8')
    saved = output / 'saved-world'
    world(saved, 0)
    phases = ('diagonal-glass-contract',) if args.glass_contract else ('diagonal-porthole-write', 'diagonal-porthole-read')
    for phase in phases:
        run(command('runServer', '-PmodernServerRunDirectory=' + str(saved),
                    '-PtransmitterPhase=' + phase, '-PdiagonalPortholeReference=' + str(reference)),
            output / (phase + '.log'), '[vandorlabs][' + phase + '] dedicated PASS',
            dict(os.environ, JAVA_HOME=str(jdk_home(21))))
        check_log(saved / 'logs/latest.log', 'All dimensions are saved')
    print('Diagonal porthole focused registration/restart PASS: ' + str(output), flush=True)

if __name__ == '__main__':
    main()
