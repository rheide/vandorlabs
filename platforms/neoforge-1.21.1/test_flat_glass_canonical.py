#!/usr/bin/env python3
"""Actual public ordinary Glass Source recipes/Survival placement/mining/ownership and process restart."""
import os,re
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from test_propulsion_world import await_shutdown
from test_flat_glass_host import evidence
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 out=Path(artifact_directory(prefix='flat-glass-canonical-',dir=ROOT/'build/test-artifacts'));saved=out/'saved-world';world(saved,0)
 for phase in ['write','read']:
  log=out/(phase+'.log')
  try:run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=flat-glass-canonical-'+phase),log,'[vandorlabs][glass-canonical] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
  finally:
   pid=re.search(r'actual server PID=(\d+)',log.read_text(errors='replace')) if log.is_file() else None
   if pid:await_shutdown(saved,int(pid.group(1)))
   evidence(out)
 print('Actual canonical ordinary Glass write/restart PASS '+str(out.relative_to(ROOT)),flush=True)
if __name__=='__main__':main()
