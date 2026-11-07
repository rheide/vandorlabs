#!/usr/bin/env python3
"""Focused private Ramp rider support and collision queries; no renderer or release gate."""
import os
import re
from test_propulsion_world import await_shutdown
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts

@retain_artifacts
def main():
    output=Path(artifact_directory(prefix='ramp-belt-native-',dir=ROOT/'build/test-artifacts'))
    saved=output/'saved-world';world(saved,0)
    run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=ramp-belt-world'),output/'server.log','[vandorlabs][ramp-belt-world] PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
    match=re.search(r'actual server PID=(\d+)',(output/'server.log').read_text(errors='replace'))
    if not match:raise RuntimeError("Actual server PID missing")
    await_shutdown(saved,int(match.group(1)))
    print('Private Ramp belt world PASS '+str(output),flush=True)

if __name__=='__main__':main()
