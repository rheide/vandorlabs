#!/usr/bin/env python3
"""Actual entity omission and failed asynchronous region write retain actor custody."""
import os
from pathlib import Path
from test_transmitter import ROOT, command, run, world, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    out=Path(artifact_directory(prefix='entity-checkpoint-failure-',dir=ROOT/'build/test-artifacts'))
    saved=out/'world';world(saved,0)
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.entityCheckpointFailure=true')
    run(command('runServer','-PmodernServerRunDirectory='+str(saved)),out/'server.log','[vandorlabs][entity-checkpoint-failure] PASS',env)
    print('Native entity checkpoint failures PASS '+str(out),flush=True)

if __name__=='__main__':main()
