#!/usr/bin/env python3
"""Canonical flat panel placement via actual Survival item use."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
 output=Path(artifact_directory(prefix='wall-against-placement-',dir=base));saved=output/'saved-world';world(saved,0)
 run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=wall-against-placement'),output/'server.log','[vandorlabs][wall-against-placement] PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
 check_log(saved/'logs/latest.log','All dimensions are saved');print('Native wall against placement PASS: '+str(output))
if __name__=='__main__':main()
