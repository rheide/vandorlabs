#!/usr/bin/env python3
"""Retired standalone landing fallback: configured stationary ownership and cold restart."""
import argparse,os,shutil
from pathlib import Path
from test_transmitter import ROOT,command,run,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--read-world',type=Path,help='Retry only cold reading a copied existing failed server runtime');args=parser.parse_args()
    out=Path(artifact_directory(prefix='legacy-landing-',dir=ROOT/'build/test-artifacts'))
    saved=out/'saved-world'
    if args.read_world:shutil.copytree(args.read_world.resolve(),saved)
    else:fixture_world(saved,0)
    for phase in (('read',) if args.read_world else ('write','read')):
        env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.telescopicLanding=true -Dvandorlabs.legacyLandingPhase='+phase)
        run(command('runServer','-PmodernServerRunDirectory='+str(saved)),out/(phase+'.log'),'[vandorlabs][legacy-landing] '+phase+' PASS',env)
    print('Retired standalone landing fallback PASS '+str(out),flush=True)
if __name__=='__main__':main()
