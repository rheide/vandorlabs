#!/usr/bin/env python3
"""Canonical private Ramp placement/copy admission and subsequent-process identities."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts

@retain_artifacts
def main():
    out=Path(artifact_directory(prefix='ramp-canonical-',dir=ROOT/'build/test-artifacts'))
    saved=out/'saved-world';world(saved,0)
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.ramps=true')
    for phase in ('write','read'):
        run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=ramp-canonical-'+phase),out/(phase+'.log'),'[vandorlabs][ramp-canonical] '+phase+' PASS',env)
    print('Canonical private Ramp placement/cold PASS '+str(out),flush=True)
if __name__=='__main__':main()
