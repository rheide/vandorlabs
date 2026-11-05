#!/usr/bin/env python3
"""Run installed native wireless endpoint pair, exact Forge scalar reference and restart checks."""
import argparse
import os
from pathlib import Path
import tempfile
from test_transmitter import ROOT,command,run,world,check_log,jdk_home

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--legacy-reference',type=Path);parser.add_argument('--configurizer-only',action='store_true');args=parser.parse_args()
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)))
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(tempfile.mkdtemp(prefix='wireless-endpoints-',dir=base));saved=output/'saved-world';world(saved,0)
    phases=['wireless-configurizer'] if args.configurizer_only else ['wireless-configurizer','wireless-closed-query','wireless-removal-observer','wireless-write','wireless-read']
    for phase in phases:
        properties=['-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+phase]
        if args.legacy_reference:properties.append('-PwirelessLegacyReference='+str(args.legacy_reference.resolve()))
        run(command('runServer',*properties),output/(phase+'.log'),'[vandorlabs][wireless] server-'+phase+' PASS',env)
        check_log(saved/'logs/latest.log','All dimensions are saved')
        if phase=='wireless-write' and args.legacy_reference:check_log(output/(phase+'.log'),'[vandorlabs][wireless] Forge-reference PASS cases=385')
    print('Native wireless endpoints focused checks PASS: '+str(output),flush=True)
if __name__=='__main__':main()
