#!/usr/bin/env python3
"""Private native Screen configured-item settings compared to actual source picks and drops."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
    out=Path(artifact_directory(prefix='screen-item-settings-',dir=ROOT/'build/test-artifacts'))
    saved=out/'saved-world';world(saved,0)
    run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=screen-item-settings','-PscreenItemReference='+str(ROOT/'build/test-artifacts/screen-item-reference.json')),out/'server.log','[vandorlabs][screen-item-settings] PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
    check_log(saved/'logs/latest.log','All dimensions are saved')
    print('Native Screen item settings focused PASS: '+str(out),flush=True)
if __name__=='__main__':main()
