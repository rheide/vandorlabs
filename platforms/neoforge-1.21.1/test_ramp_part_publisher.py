#!/usr/bin/env python3
"""Prepared Ramp bodies in an actual durable operation scope; no public controller acceptance."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts

@retain_artifacts
def main():
    output=Path(artifact_directory(prefix='ramp-publisher-native-',dir=ROOT/'build/test-artifacts'))
    saved=output/'saved-world';world(saved,0)
    for phase in ['write','read']:
        run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=ramp-publisher-'+phase),output/(phase+'.log'),'[vandorlabs][ramp-publisher] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
    print('Private prepared Ramp publication PASS '+str(output),flush=True)

if __name__=='__main__':main()
