#!/usr/bin/env python3
"""Public Large Door native player actions and emitted 1.12 layouts across a complete server restart."""
import argparse
import os
from pathlib import Path
from test_transmitter import ROOT, command, run, world, check_log, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--legacy-reference',type=Path,required=True);args=parser.parse_args();reference=args.legacy_reference.resolve()
    if not reference.is_file():parser.error('existing exportLargeDoorSavedReference output required')
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);output=Path(artifact_directory(prefix='large-door-canonical-native-',dir=base));saved=output/'saved-world';world(saved,0)
    for phase in ['write','read']:
        run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=large-door-canonical-'+phase,'-PlargeDoorSavedReference='+str(reference)),output/(phase+'.log'),'[vandorlabs][large-door-canonical] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
        check_log(saved/'logs/latest.log','All dimensions are saved')
    print('Actual canonical Large Door dedicated acceptance PASS: '+str(output),flush=True)
if __name__=='__main__':main()
