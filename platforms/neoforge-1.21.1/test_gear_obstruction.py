#!/usr/bin/env python3
"""Focused obstruction return/native inventory restoration and clear retry."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 out=Path(artifact_directory(prefix='gear-obstruction-',dir=ROOT/'build/test-artifacts'));saved=out/'saved-world';fixture_world(saved,0)
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.gearRuntime=true -Dvandorlabs.gearObstructionCheck=true')
 run(command('runServer','-PmodernServerRunDirectory='+str(saved)),out/'server.log','[vandorlabs][gear-obstruction] PASS',env)
 print('Native arm obstruction PASS '+str(out),flush=True)
if __name__=='__main__':main()
