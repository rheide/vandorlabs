#!/usr/bin/env python3
"""Copy-specific actual ARMED withdrawal, source-only save, abrupt stop and cold exact rollback."""
import os
from pathlib import Path
from test_transmitter import ROOT, command, run, jdk_home, launch, stop
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    out=Path(artifact_directory(prefix='copy-recovery-',dir=ROOT/'build/test-artifacts'))
    saved=out/'saved-world'
    fixture_world(saved,0)
    common=dict(os.environ,JAVA_HOME=str(jdk_home(21)))
    properties=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.copyRuntimeChecks=true'
    env=dict(common,JAVA_TOOL_OPTIONS=properties+' -Dvandorlabs.copyPhase=crash')
    log=out/'crash.log'
    process=launch(command('runServer','-PmodernServerRunDirectory='+str(saved)),log,env)
    try:
        result=process.wait(timeout=180)
        text=log.read_text(errors='replace')
        marker='[vandorlabs][copy-runtime] crash PASS'
        if result or marker not in text or 'Stopping server' in text.split(marker)[-1]:
            raise RuntimeError('Copy abrupt partial material checkpoint missing: '+str(log))
    finally:stop(process)
    env=dict(common,JAVA_TOOL_OPTIONS=properties+' -Dvandorlabs.copyPhase=read')
    run(command('runServer','-PmodernServerRunDirectory='+str(saved)),out/'read.log','[vandorlabs][copy-runtime] read PASS',env)
    print('Copy material withdrawal abrupt recovery PASS '+str(out),flush=True)
if __name__=='__main__':main()
