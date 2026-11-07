#!/usr/bin/env python3
"""Private canonical deferred Canopy definitions and complete saved-world restart."""
import os,argparse
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--inactive-only',action='store_true');args=parser.parse_args()
    out=Path(artifact_directory(prefix='canopy-definitions-',dir=ROOT/'build/test-artifacts'));saved=out/'saved-world';world(saved,0)
    for phase in (('inactive',) if args.inactive_only else ('inactive','write','read')):
        run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=canopy-definitions-'+phase),out/(phase+'.log'),'[vandorlabs][canopy-definitions] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
        check_log(saved/'logs/latest.log','All dimensions are saved')
    print('Canopy canonical definitions PASS '+str(out),flush=True)
if __name__=='__main__':main()
