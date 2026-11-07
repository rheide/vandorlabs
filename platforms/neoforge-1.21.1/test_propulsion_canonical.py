#!/usr/bin/env python3
"""Actual canonical recipes, Survival item/tool paths and whole-process owner restart."""
import os,re
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from test_propulsion_world import await_shutdown
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
 out=Path(artifact_directory(prefix='propulsion-canonical-',dir=base));saved=out/'saved-world';world(saved,0)
 for phase in ['write','read']:
  log=out/(phase+'.log')
  run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=propulsion-canonical-'+phase),log,'[vandorlabs][propulsion-canonical] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
  pid=re.search(r'actual server PID=(\d+)',log.read_text(errors='replace'))
  if not pid:raise RuntimeError('Canonical fixture did not identify actual process')
  await_shutdown(saved,int(pid.group(1)))
 print('Canonical standalone propulsion write/restart PASS '+str(out),flush=True)
if __name__=='__main__':main()
