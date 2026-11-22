#!/usr/bin/env python3
"""Exercise native vehicle cell/actor ownership, including abrupt cold recovery."""
import argparse
import hashlib
import os
from pathlib import Path

from native_test_artifacts import artifact_directory, retain_artifacts
from navigation_test_artifact import snapshot
from test_sampled_materials import fixture_world
from test_transmitter import ROOT, command, jdk_home, run


@retain_artifacts
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--group', choices=('all', 'roundtrip', 'arms', 'assembly-recovery', 'parking-recovery'), default='all')
    parser.add_argument('--mekanism', type=Path, help='Use this installed Mekanism JAR in the roundtrip fuel case')
    arguments = parser.parse_args()
    if arguments.mekanism and (arguments.group != 'roundtrip' or not arguments.mekanism.is_file()):
        parser.error('--mekanism requires an installed JAR and --group roundtrip')
    base = ROOT / 'build/test-artifacts'
    base.mkdir(parents=True, exist_ok=True)
    output = Path(artifact_directory(prefix='vehicle-transfer-', dir=base))
    candidate = snapshot(output, 'before')
    opts = os.environ.get('JAVA_TOOL_OPTIONS', '')
    if arguments.mekanism:
        jar = arguments.mekanism.resolve()
        platform = Path(__file__).resolve().parent
        folders = os.pathsep.join(str(platform / 'build' / part) for part in
                                  ('classes/java/main', 'resources/main', 'classes/java/smoke', 'resources/smoke'))
        properties = output / 'optional-mod-folders.properties'
        with properties.open('w', encoding='ascii') as out:
            for key, value in [('vandorlabs', folders), ('mekanism', str(jar))]:
                escaped = value.replace('\\', '\\\\').encode('utf-16-be')
                units = (int.from_bytes(escaped[i:i+2], 'big') for i in range(0, len(escaped), 2))
                out.write(key + '=' + ''.join(chr(n) if 32 <= n < 127 else f'\\u{n:04x}' for n in units) + '\n')
        (output / 'mekanism.sha256').write_text(hashlib.sha256(jar.read_bytes()).hexdigest() + '  ' + jar.name + '\n')
        opts += ' -Dvandorlabs.vehicleMekanismTank=true -Dfml.modFoldersFile="' + str(properties) + '"'
    groups = {'roundtrip': ('roundtrip',), 'arms': ('arms',), 'assembly-recovery': ('crash-assemble', 'read-assemble'),
              'parking-recovery': ('crash-park', 'read-park')}
    for name, phases in groups.items():
        if arguments.group not in ('all', name):
            continue
        world = output / phases[0] / 'saved-world'
        fixture_world(world, 0)
        for phase in phases:
            log = output / (phase + '.log')
            label = '[vandorlabs][vehicle-transfer] ' + phase + ' PASS'
            env = dict(os.environ, JAVA_HOME=str(jdk_home(21)), JAVA_TOOL_OPTIONS=opts
                       + ' -Dvandorlabs.cellJournalFaults=true -Dvandorlabs.vehicleTransferPhase=' + phase)
            run(command('runServer', '-PmodernServerRunDirectory=' + str(world),
                        '-x', 'compileJava', '-x', 'compileSmokeJava', '-x', 'processResources'), log, label, env)
            if arguments.mekanism and '[vandorlabs][vehicle-transfer] installed Mekanism tank PASS' not in log.read_text(errors='replace'):
                raise AssertionError('Mekanism fuel transfer evidence missing: ' + str(log))
            if phase.startswith('crash-'):
                text = log.read_text(errors='replace')
                if 'Stopping server' in text[text.index(label):]:
                    raise AssertionError('Fault writer reached graceful shutdown: ' + str(log))
    snapshot(output, 'after', candidate, require_source_unchanged=False)
    print('Native vehicle ' + arguments.group + ' checks passed. ' + str(output), flush=True)


if __name__ == '__main__':
    main()
