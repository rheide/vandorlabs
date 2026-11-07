#!/usr/bin/env python3
"""Actual native registry/DFU inventory-item staging against released Source envelopes."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
    output=Path(artifact_directory(prefix='storage-codec-native-',dir=ROOT/'build/test-artifacts'));saved=output/'server';world(saved,0)
    run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=storage-codec'),output/'run.log','[vandorlabs][storage-codec] PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
    check_log(saved/'logs/latest.log','All dimensions are saved')
    print('Native Storage actual item codec PASS: '+str(output),flush=True)
if __name__=='__main__':main()
