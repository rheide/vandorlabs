#!/usr/bin/env python3
"""Actual chunk light-source discovery for public native contextual emitters."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 output=Path(artifact_directory(prefix='dynamic-light-native-',dir=ROOT/'build/test-artifacts'));saved=output/'saved-world';world(saved,0)
 run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=dynamic-light'),output/'server.log','[vandorlabs][dynamic-light] dedicated PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
 check_log(saved/'logs/latest.log','All dimensions are saved')
 print('Native contextual light source focus PASS: '+str(output),flush=True)
if __name__=='__main__':main()
