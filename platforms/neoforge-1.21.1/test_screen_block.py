#!/usr/bin/env python3
"""Inactive Screen block domains and actual engine collision unions in an isolated dedicated process."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);output=Path(artifact_directory(prefix='screen-block-native-',dir=base));saved=output/'world';world(saved,0)
 run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=screen-block'),output/'run.log','Native Screen block foundation PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
 check_log(saved/'logs/latest.log','All dimensions are saved')
 print('Inactive native Screen block foundation PASS: '+str(output),flush=True)
if __name__=='__main__':main()
