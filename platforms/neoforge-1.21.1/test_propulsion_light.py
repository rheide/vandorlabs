#!/usr/bin/env python3
"""Private real world/state auxiliary light-emission contract."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='propulsion-light-world-',dir=base));saved=out/'saved-world';world(saved,0)
 run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=propulsion-light-write'),out/'write.log','[vandorlabs][propulsion-light] write PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
 check_log(saved/'logs/latest.log','All dimensions are saved');print('Native propulsion actual world light PASS: '+str(out),flush=True)
if __name__=='__main__':main()
