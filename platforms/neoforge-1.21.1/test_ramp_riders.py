#!/usr/bin/env python3
"""Focused private Ramp rider support and collision queries; no renderer or release gate."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts

@retain_artifacts
def main():
    output=Path(artifact_directory(prefix='ramp-riders-native-',dir=ROOT/'build/test-artifacts'))
    saved=output/'saved-world';world(saved,0)
    run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=ramp-rider-world'),output/'server.log','[vandorlabs][ramp-rider-world] PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
    print('Private Ramp rider world PASS '+str(output),flush=True)

if __name__=='__main__':main()
