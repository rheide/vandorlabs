#!/usr/bin/env python3
"""Focused native Rack physical modules, typed settings and event-driven channels."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
    out=Path(artifact_directory(prefix='rack-runtime-',dir=ROOT/'build/test-artifacts'));saved=out/'saved-world';fixture_world(saved,0)
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.moduleRack=true -Dvandorlabs.rackRuntimeChecks=true')
    run(command('runServer','-PmodernServerRunDirectory='+str(saved)),out/'server.log','[vandorlabs][rack-runtime] PASS',env)
    print('Native Rack physical module integration PASS '+str(out),flush=True)
if __name__=='__main__':main()
