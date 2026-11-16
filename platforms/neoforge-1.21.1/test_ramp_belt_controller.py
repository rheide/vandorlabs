#!/usr/bin/env python3
"""One actual controller lifecycle over asynchronous preparation and scheduled engine ticks."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts

@retain_artifacts
def main():
    (ROOT/'build/test-artifacts').mkdir(parents=True,exist_ok=True)
    output=Path(artifact_directory(prefix='ramp-belt-controller-native-',dir=ROOT/'build/test-artifacts'))
    saved=output/'saved-world';world(saved,0)
    run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=ramp-belt-controller'),output/'write.log','[vandorlabs][ramp-belt-controller] PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
    print('Native Ramp scheduled belt PASS: '+str(output),flush=True)

if __name__=='__main__':main()
