#!/usr/bin/env python3
"""Small dedicated check of the actual native slab/stair registration and configured item boundaries."""
import argparse
import os
import tempfile
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--editor-only',action='store_true');args=parser.parse_args()
    phase='shape-editor' if args.editor_only else 'shape-registration'
    marker='[vandorlabs]['+phase+'] dedicated PASS'
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(artifact_directory(prefix='shape-registration-',dir=base));saved=output/'saved-world';world(saved,0)
    run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+phase),output/'registration.log',marker,dict(os.environ,JAVA_HOME=str(jdk_home(21))))
    check_log(saved/'logs/latest.log','All dimensions are saved')
    print('Native shape registration focused checks PASS: '+str(output),flush=True)
if __name__=='__main__':main()
