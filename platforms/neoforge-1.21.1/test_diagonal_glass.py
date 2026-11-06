#!/usr/bin/env python3
"""Focused canonical diagonal glass registration and independent saved-world restart."""
import argparse
import hashlib
import os
from pathlib import Path
from native_test_artifacts import artifact_directory, retain_artifacts
from test_transmitter import ROOT, command, run, world, check_log, jdk_home

@retain_artifacts
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--legacy-reference', type=Path, required=True)
    parser.add_argument('--geometry-reference', type=Path, required=True)
    args = parser.parse_args()
    references = [args.legacy_reference.resolve(), args.geometry_reference.resolve()]
    if any(not path.is_file() for path in references):
        parser.error('Actual Forge factory/light and physical geometry exports required')
    base = ROOT / 'build/test-artifacts'
    base.mkdir(parents=True, exist_ok=True)
    output = Path(artifact_directory(prefix='diagonal-glass-native-', dir=base))
    (output / 'references.sha256').write_text(''.join(hashlib.sha256(path.read_bytes()).hexdigest() + '  ' + path.name + '\n' for path in references), encoding='utf-8')
    saved = output / 'saved-world'
    world(saved, 0)
    for phase in ('diagonal-glass-write', 'diagonal-glass-read'):
        run(command('runServer', '-PmodernServerRunDirectory=' + str(saved),
                    '-PtransmitterPhase=' + phase,
                    '-PdiagonalGlassRegistrationReference=' + str(references[0]),
                    '-PdiagonalGlassGeometryReference=' + str(references[1])),
            output / (phase + '.log'), '[vandorlabs][' + phase + '] dedicated PASS',
            dict(os.environ, JAVA_HOME=str(jdk_home(21))))
        check_log(saved / 'logs/latest.log', 'All dimensions are saved')
    print('Diagonal glass focused registration/restart PASS: ' + str(output), flush=True)

if __name__ == '__main__':
    main()
