#!/usr/bin/env python3
"""Real asynchronous arm capture must keep reentrant callback inventory access frozen."""
import os,argparse
from pathlib import Path
from test_transmitter import ROOT, command, run, world, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--escrow-only',action='store_true');args=parser.parse_args()
    out=Path(artifact_directory(prefix='gear-reentrant-',dir=ROOT/'build/test-artifacts'))
    saved=out/'world';world(saved,0)
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.gearJournalPhase=reentrant'+(' -Dvandorlabs.gearEscrowOnly=true' if args.escrow_only else ''))
    run(command('runServer','-PmodernServerRunDirectory='+str(saved)),out/'server.log','[vandorlabs][gear-reentrant] PASS',env)
    print('Native arm reentrant capture PASS '+str(out),flush=True)

if __name__=='__main__':main()
