#!/usr/bin/env python3
"""Run actual installed four-detent numeric host and saved-bank checks."""
import argparse
import os
from pathlib import Path
import tempfile
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--legacy-reference',type=Path);args=parser.parse_args()
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)))
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(artifact_directory(prefix='signal-controls-',dir=base));saved=output/'saved-world';world(saved,0)
    for phase in ['signal-write','signal-read']:
        properties=['-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+phase]
        if args.legacy_reference:properties.append('-PsignalLegacyReference='+str(args.legacy_reference.resolve()))
        run(command('runServer',*properties),output/(phase+'.log'),'[vandorlabs][signal-controls] server-'+phase+' PASS',env)
        check_log(saved/'logs/latest.log','All dimensions are saved')
        if phase=='signal-write' and args.legacy_reference:check_log(output/(phase+'.log'),'[vandorlabs][signal-controls] Forge-reference PASS cases=4840')
    print('Native signal control focused checks PASS: '+str(output),flush=True)
if __name__=='__main__':main()
