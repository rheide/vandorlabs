#!/usr/bin/env python3
"""Dedicated current wall/frame save replay; renderer and player-widget acceptance remain separate."""
import argparse
import os
from pathlib import Path
import tempfile
from test_transmitter import ROOT,command,run,world,check_log,jdk_home

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--legacy-reference',required=True,type=Path);args=parser.parse_args();reference=args.legacy_reference.resolve()
    if not reference.is_file():parser.error('Actual Forge testWallFrameLegacy reference required')
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(tempfile.mkdtemp(prefix='wall-host-native-',dir=base));saved=output/'saved-world';world(saved,0)
    run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=wall-host','-PwallFrameReference='+str(reference)),output/'persistence.log','[vandorlabs][wall-host] dedicated PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
    check_log(saved/'logs/latest.log','All dimensions are saved');print('Native wall host focused checks PASS: '+str(output),flush=True)
if __name__=='__main__':main()
