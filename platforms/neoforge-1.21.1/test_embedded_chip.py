#!/usr/bin/env python3
"""Focused native chip text decoding against the released journal corpus."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    phase='embedded-chip'
    marker='[vandorlabs]['+phase+'] dedicated PASS'
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(artifact_directory(prefix='embedded-chip-',dir=base));saved=output/'saved-world';world(saved,0)
    run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+phase,'-PembeddedChipReference='+str(ROOT/'build/test-artifacts/embedded-chip-reference.json')),output/'registration.log',marker,dict(os.environ,JAVA_HOME=str(jdk_home(21))))
    check_log(saved/'logs/latest.log','All dimensions are saved')
    print('Native embedded chip reader focused checks PASS: '+str(output),flush=True)
if __name__=='__main__':main()
