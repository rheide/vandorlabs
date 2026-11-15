#!/usr/bin/env python3
"""Native programmable pressure plate sensing and independent channel output."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts

@retain_artifacts
def main():
    out=Path(artifact_directory(prefix='plate-',dir=ROOT/'build/test-artifacts'))
    saved=out/'saved-world'
    fixture_world(saved,0)
    for phase in ('write','read'):
        env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.pressurePlate=true -Dvandorlabs.platePhase='+phase)
        run(command('runServer','-PmodernServerRunDirectory='+str(saved)),out/(phase+'.log'),'[vandorlabs][plate-runtime] '+phase+' PASS',env)
    print('Native Programmable Pressure Plate PASS '+str(out),flush=True)

if __name__=='__main__':main()
