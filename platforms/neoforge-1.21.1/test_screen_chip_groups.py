#!/usr/bin/env python3
"""Actual private loaded Screen/reader replay against the released chip-group corpus."""
import os
from test_transmitter import ROOT,command,run,world,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
 output=artifact_directory(prefix='screen-chip-groups-',dir=base)
 from pathlib import Path
 output=Path(output);saved=output/'world';world(saved,0)
 run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=screen-block-chip-groups'),output/'server.log','[vandorlabs][screen-chip-groups] PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_READER_SCREEN_CHECK='true'))
 print('Native Screen chip groups focused world PASS: '+str(output),flush=True)
if __name__=='__main__':main()
