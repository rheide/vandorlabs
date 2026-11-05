#!/usr/bin/env python3
"""Dedicated native two-slot codec checks against an actual Forge compressed-NBT oracle."""
import argparse
import os
from pathlib import Path
import tempfile
from test_transmitter import ROOT,command,run,world,check_log,jdk_home

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--legacy-reference',required=True,type=Path);args=parser.parse_args()
    reference=args.legacy_reference.resolve()
    if not reference.is_file():parser.error('Legacy reference must be an actual Forge testFluidSlotsLegacy export')
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(tempfile.mkdtemp(prefix='fluid-slots-native-',dir=base));saved=output/'saved-world';world(saved,0)
    run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=fluid-slots','-PfluidSlotsReference='+str(reference)),output/'storage.log','[vandorlabs][fluid-slots] dedicated PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
    check_log(saved/'logs/latest.log','All dimensions are saved')
    print('Native fluid slots focused checks PASS: '+str(output),flush=True)
if __name__=='__main__':main()
