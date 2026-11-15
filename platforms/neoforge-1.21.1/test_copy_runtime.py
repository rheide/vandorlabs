#!/usr/bin/env python3
"""One native Copy Controller async paired-pad material ownership path."""
import os
from pathlib import Path
from test_transmitter import ROOT, command, run, jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    out=Path(artifact_directory(prefix='copy-runtime-',dir=ROOT/'build/test-artifacts'))
    world=out/'saved-world'
    fixture_world(world,0)
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.copyControllers=true -Dvandorlabs.copyRuntimeChecks=true')
    run(command('runServer','-PmodernServerRunDirectory='+str(world)),out/'server.log','[vandorlabs][copy-runtime] PASS',env)
    print('Native Copy Controller runtime PASS '+str(out),flush=True)
if __name__=='__main__':main()
