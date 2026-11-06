#!/usr/bin/env python3
"""Focused native Trapdoor original platform-data retention and superclass failure recovery."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='trapdoor-load-guard-',dir=base));saved=out/'saved-world';world(saved,0)
 for phase in ['write','read']:
  run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=trapdoor-load-guard-'+phase),out/(phase+'.log'),'[vandorlabs][trapdoor-load-guard] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
  check_log(saved/'logs/latest.log','All dimensions are saved')
 print('Native Trapdoor load guard focused PASS: '+str(out),flush=True)
if __name__=='__main__':main()
