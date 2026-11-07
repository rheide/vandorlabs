#!/usr/bin/env python3
"""Private ordinary Glass Source item callbacks, Survival transaction and whole-process restart."""
import os,re
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from test_propulsion_world import await_shutdown
from test_flat_glass_host import evidence
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
 out=Path(artifact_directory(prefix='flat-glass-item-',dir=base));saved=out/'saved-world';world(saved,0)
 for phase in ['write','read']:
  log=out/(phase+'.log')
  try:run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=flat-glass-item-'+phase),log,'[vandorlabs][flat-glass-item] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_FLAT_GLASS_ITEM_REFERENCE=str(ROOT/'build/test-artifacts/flat-glass-item-forge.jsonl')))
  finally:
   pid=re.search(r'actual server PID=(\d+)',log.read_text(errors='replace')) if log.is_file() else None
   if pid:await_shutdown(saved,int(pid.group(1)))
   evidence(out)
 print('Private ordinary Glass item/ownership/restart PASS '+str(out),flush=True)
if __name__=='__main__':main()
