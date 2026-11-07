#!/usr/bin/env python3
"""Private native server callbacks against the released chip interaction corpus."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='screen-chip-interactions-',dir=base));saved=out/'world';world(saved,0)
 run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=screen-block-chip-interactions'),out/'server.log','[vandorlabs][screen-chip-interaction] PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_READER_SCREEN_CHECK='true'))
 print('Native private Screen chip interaction callback focus PASS: '+str(out),flush=True)
if __name__=='__main__':main()
