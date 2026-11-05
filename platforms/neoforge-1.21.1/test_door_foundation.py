#!/usr/bin/env python3
"""Actual registry/NBT/codec checks for inactive door foundations, excluding placed gameplay."""
import argparse
import os
from pathlib import Path
from test_transmitter import ROOT, command, run, world, check_log, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--legacy-reference',type=Path,required=True);args=parser.parse_args()
    reference=args.legacy_reference.resolve()
    if not reference.is_file():parser.error('existing exportDoorSettingsReference output required')
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(artifact_directory(prefix='door-foundation-native-',dir=base));saved=output/'saved-world';world(saved,0)
    run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=door-foundation','-PdoorSettingsReference='+str(reference)),output/'foundation.log','[vandorlabs][door-foundation] dedicated PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
    check_log(saved/'logs/latest.log','All dimensions are saved');print('Native door foundation focused checks PASS: '+str(output),flush=True)
if __name__=='__main__':main()
