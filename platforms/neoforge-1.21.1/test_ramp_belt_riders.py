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
    for phase,marker,name in [('ramp-belt-world','[vandorlabs][ramp-belt-world] PASS','write'),('ramp-belt-world-read','[vandorlabs][ramp-belt-world] restart PASS','read')]:
        log=output/(name+'.log')
        match=None
        try:
            run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+phase),log,marker,dict(os.environ,JAVA_HOME=str(jdk_home(21))))
        finally:
            match=re.search(r'actual server PID=(\d+)',log.read_text(errors='replace')) if log.exists() else None
            if match:await_shutdown(saved,int(match.group(1)))
        if not match:raise RuntimeError('Actual server PID missing')
    print('Private Ramp belt world PASS '+str(output),flush=True)

if __name__=='__main__':main()
