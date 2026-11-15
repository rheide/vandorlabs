#!/usr/bin/env python3
"""Read-only exact native modular after-image staging for old integrated wheels."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
    out=Path(artifact_directory(prefix='legacy-destination-',dir=ROOT/'build/test-artifacts'))
    saved=out/'world';world(saved,0)
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.legacyDestinationChecks=true')
    run(command('runServer','-PmodernServerRunDirectory='+str(saved)),out/'server.log','[vandorlabs][legacy-destination] PASS',env)
    print('Native legacy modular destination staging PASS '+str(out),flush=True)
if __name__=='__main__':main()
