#!/usr/bin/env python3
"""Actual vanilla piston publication against durable Ramp held sources and restart."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
 output=Path(artifact_directory(prefix='ramp-held-physics-',dir=base));saved=output/'saved-world';world(saved,0)
 for phase in ['write','read']:
  marker='ramp-held-physics-'+phase
  run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+marker),output/(phase+'.log'),'[vandorlabs][ramp-held-physics] '+marker+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
  check_log(saved/'logs/latest.log','All dimensions are saved')
 print('Native Ramp held physics focused PASS: '+str(output),flush=True)
if __name__=='__main__':main()
