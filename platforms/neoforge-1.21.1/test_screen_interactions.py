#!/usr/bin/env python3
"""Focused actual vanilla Screen block-use dispatch and row authority."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    phase='screen-block-interactions'
    marker='[vandorlabs][screen-interactions] dedicated PASS'
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(artifact_directory(prefix='screen-interactions-',dir=base));saved=output/'saved-world';world(saved,0)
    run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+phase),output/'registration.log',marker,dict(os.environ,JAVA_HOME=str(jdk_home(21))))
    check_log(saved/'logs/latest.log','All dimensions are saved')
    print('Native Screen interaction focused checks PASS: '+str(output),flush=True)
if __name__=='__main__':main()
