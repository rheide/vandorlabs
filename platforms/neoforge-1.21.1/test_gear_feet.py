#!/usr/bin/env python3
"""Private functional arm placement, redstone/reversal and real process restart."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts

@retain_artifacts
def main():
    out=Path(artifact_directory(prefix='gear-feet-',dir=ROOT/'build/test-artifacts'))
    saved=out/'saved-world'
    world(saved,0)
    for phase in ('write','read'):
        env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.gearRuntime=true -Dvandorlabs.gearFeetPhase='+phase)
        run(command('runServer','-PmodernServerRunDirectory='+str(saved)),out/(phase+'.log'),'[vandorlabs][gear-feet] '+phase+' PASS',env)
    print('Private native arm feet PASS '+str(out),flush=True)

if __name__=='__main__':main()
