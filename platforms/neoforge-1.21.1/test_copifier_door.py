#!/usr/bin/env python3
"""Installed ordinary and Large Door copied settings, owner resolution and undo."""
import os,argparse
from pathlib import Path
from test_transmitter import ROOT, command, run, world, check_log, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--large',action='store_true');options=parser.parse_args();kind='copifier-large-door' if options.large else 'copifier-door'
    out = Path(artifact_directory(prefix=kind+'-', dir=ROOT/'build/test-artifacts'))
    saved = out/'saved-world'
    world(saved, 0)
    reference = ROOT/('testclient/fixtures/'+kind+'-source.json.gz')
    for phase in ['write', 'read']:
        marker = '[vandorlabs][copifier-door] '+phase+' PASS'
        run(command('runServer', '-PmodernServerRunDirectory='+str(saved), '-PtransmitterPhase='+kind+'-'+phase, '-PcopifierDoorReference='+str(reference)), out/(phase+'.log'), marker, dict(os.environ, JAVA_HOME=str(jdk_home(21))))
        check_log(saved/'logs/latest.log', 'All dimensions are saved')
    print('Native installed Door copy PASS: '+str(out), flush=True)

if __name__ == '__main__':
    main()
