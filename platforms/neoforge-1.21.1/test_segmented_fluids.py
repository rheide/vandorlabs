#!/usr/bin/env python3
"""Inactive stationary Segmented fluid ownership, actual use packets/vanilla flow and complete-process restart."""
import argparse
import hashlib
import os
from pathlib import Path
from test_transmitter import ROOT, command, run, world, check_log, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--legacy-reference',type=Path,required=True);args=parser.parse_args();reference=args.legacy_reference.resolve()
    if not reference.is_file():parser.error('existing exportSegmentedFluidReference gush output required')
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);output=Path(artifact_directory(prefix='segmented-fluid-native-',dir=base));saved=output/'saved-world';world(saved,0)
    (output/'reference.sha256').write_text(hashlib.sha256(reference.read_bytes()).hexdigest()+'  '+reference.name+'\n')
    for phase in ['write','read']:
        run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=segmented-fluid-'+phase,'-PsegmentedFluidReference='+str(reference)),output/(phase+'.log'),'[vandorlabs][segmented-fluid] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
        check_log(saved/'logs/latest.log','All dimensions are saved')
    print('Native stationary Segmented fluids focused PASS: '+str(output),flush=True)
if __name__=='__main__':main()
