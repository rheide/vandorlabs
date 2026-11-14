#!/usr/bin/env python3
"""Focused actual native entity checkpoint followed by abrupt JVM termination and process reload."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home,launch,stop
from native_test_artifacts import artifact_directory,retain_artifacts

@retain_artifacts
def main():
    out=Path(artifact_directory(prefix='gear-actor-checkpoint-',dir=ROOT/'build/test-artifacts'))
    saved=out/'saved-world'
    world(saved,0)
    common=dict(os.environ,JAVA_HOME=str(jdk_home(21)))
    env=dict(common,JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.gearRuntime=true -Dvandorlabs.gearActorCheck=write')
    log=out/'write.log'
    process=launch(command('runServer','-PmodernServerRunDirectory='+str(saved)),log,env)
    try:
        result=process.wait(timeout=180)
        text=log.read_text(errors='replace')
        if result==0 or '[vandorlabs][gear-actor-checkpoint] write PASS' not in text or 'non-zero exit value 137' not in text:
            raise RuntimeError('Actual HALT137 checkpoint proof missing: '+str(log))
    finally:stop(process)
    env=dict(common,JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.gearRuntime=true -Dvandorlabs.gearActorCheck=read')
    run(command('runServer','-PmodernServerRunDirectory='+str(saved)),out/'read.log','[vandorlabs][gear-actor-checkpoint] read PASS',env)
    print('Native actor checkpoint abrupt durability PASS '+str(out),flush=True)

if __name__=='__main__':main()
