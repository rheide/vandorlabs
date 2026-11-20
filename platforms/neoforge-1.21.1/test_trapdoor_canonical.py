#!/usr/bin/env python3
"""Public Trapdoor registry, configured item, recipe, harvest and saved-owner acceptance."""
import os
import argparse
from pathlib import Path
from test_transmitter import ROOT, command, run, world, check_log, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts


@retain_artifacts
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--replay-read',type=Path)
    args=parser.parse_args()
    output = Path(artifact_directory(prefix='trapdoor-canonical-', dir=ROOT/'build/test-artifacts'))
    if args.replay_read:
        previous=args.replay_read.resolve()
        if previous.parent!=(ROOT/'build/test-artifacts').resolve() or not previous.name.startswith('trapdoor-canonical-') or not (previous/'saved-world/world/level.dat').is_file():
            parser.error('--replay-read requires a retained canonical Trapdoor runtime')
        saved=previous/'saved-world'
    else:
        saved = output/'saved-world'
        world(saved, 0)
    for phase in (('read',) if args.replay_read else ('write', 'read')):
        run(command('runServer', '-PmodernServerRunDirectory='+str(saved), '-PtransmitterPhase=trapdoor-canonical-'+phase), output/(phase+'.log'), '[vandorlabs][trapdoor-canonical] '+phase+' PASS', dict(os.environ, JAVA_HOME=str(jdk_home(21))))
        check_log(saved/'logs/latest.log', 'All dimensions are saved')
    print('Native canonical Trapdoor focused checks PASS: '+str(output), flush=True)


if __name__ == '__main__':
    main()
