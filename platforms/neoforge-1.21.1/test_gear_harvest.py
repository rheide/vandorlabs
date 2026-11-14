#!/usr/bin/env python3
"""Captured Survival harvest through asynchronous terrain and native actor ownership barriers."""
import os,argparse
from pathlib import Path
from test_transmitter import ROOT,command,run,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts

@retain_artifacts
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--case',choices=['normal','recovery'])
    args=parser.parse_args()
    out=Path(artifact_directory(prefix='gear-harvest-',dir=ROOT/'build/test-artifacts'))
    for name,phases in [('normal',['normal']),('recovery',['crash','recover'])]:
        if args.case and args.case!=name:continue
        saved=out/'saved-world'/name
        fixture_world(saved,0)
        for phase in phases:
            env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.gearRuntime=true -Dvandorlabs.gearJournalPhase=harvest -Dvandorlabs.gearHarvestPhase='+phase)
            run(command('runServer','-PmodernServerRunDirectory='+str(saved)),out/(phase+'.log'),'[vandorlabs][gear-harvest] '+phase+' PASS',env)
    print('Private native captured harvest PASS '+str(out),flush=True)

if __name__=='__main__':main()
