#!/usr/bin/env python3
"""Run installed native binary controls and scheduled-release restart checks."""
import argparse
import os
from pathlib import Path
import tempfile
from test_transmitter import ROOT,command,run,world,check_log,jdk_home

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--legacy-reference',type=Path);args=parser.parse_args()
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)))
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(tempfile.mkdtemp(prefix='binary-controls-',dir=base));saved=output/'saved-world';world(saved,0)
    for phase in ['binary-write','binary-read']:
        properties=['-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+phase]
        if args.legacy_reference:properties.append('-PbinaryLegacyReference='+str(args.legacy_reference.resolve()))
        run(command('runServer',*properties),output/(phase+'.log'),'[vandorlabs][binary-controls] server-'+phase+' PASS',env)
        check_log(saved/'logs/latest.log','All dimensions are saved')
        if phase=='binary-write' and args.legacy_reference:check_log(output/(phase+'.log'),'[vandorlabs][binary-controls] Forge-bank-reference PASS cases=1280')
    print('Native binary controls focused checks PASS: '+str(output),flush=True)
if __name__=='__main__':main()
