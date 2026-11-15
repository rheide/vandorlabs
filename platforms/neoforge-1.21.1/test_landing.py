#!/usr/bin/env python3
"""Standalone side-wheel item placement, footprints, mining, collision and cold saved states."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts

@retain_artifacts
def main():
    out=Path(artifact_directory(prefix='landing-',dir=ROOT/'build/test-artifacts'))
    saved=out/'saved-world'
    fixture_world(saved,0)
    for phase in ('write','read'):
        env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.legacyLanding=true -Dvandorlabs.landingPhase='+phase)
        run(command('runServer','-PmodernServerRunDirectory='+str(saved)),out/(phase+'.log'),'[vandorlabs][modern] landing-'+phase+' PASS',env)
    print('Native standalone landing wheels PASS '+str(out),flush=True)

if __name__=='__main__':main()
