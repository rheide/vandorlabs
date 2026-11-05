#!/usr/bin/env python3
"""Replay actual current Forge stair/default NBT into the unregistered native shape host."""
import argparse
import os
from pathlib import Path
import tempfile
from test_transmitter import ROOT,command,run,world,check_log,jdk_home

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--stair-reference',required=True,type=Path);parser.add_argument('--defaults-reference',required=True,type=Path);args=parser.parse_args()
    references=[args.stair_reference.resolve(),args.defaults_reference.resolve()]
    if not all(path.is_file() for path in references):parser.error('Both actual Forge reference exports are required')
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(tempfile.mkdtemp(prefix='shape-host-native-',dir=base));saved=output/'saved-world';world(saved,0)
    run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=shape-host','-PstairReference='+str(references[0]),'-PshapeDefaultsReference='+str(references[1])),output/'persistence.log','[vandorlabs][shape-host] dedicated PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
    check_log(saved/'logs/latest.log','All dimensions are saved')
    print('Native shape host focused checks PASS: '+str(output),flush=True)
if __name__=='__main__':main()
