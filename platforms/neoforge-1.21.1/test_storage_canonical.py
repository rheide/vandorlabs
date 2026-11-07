#!/usr/bin/env python3
"""Canonical Storage real GameMode/capability/menu/items/properties and complete restart."""
import os,re,hashlib
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from test_propulsion_world import await_shutdown
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 out=Path(artifact_directory(prefix='storage-canonical-',dir=ROOT/'build/test-artifacts'));saved=out/'saved-world';world(saved,0)
 for phase in ['write','read']:
  log=out/(phase+'.log')
  try:run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=storage-canonical-'+phase),log,'[vandorlabs][storage-canonical] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
  finally:
   pid=re.search(r'actual server PID=(\d+)',log.read_text(errors='replace')) if log.is_file() else None
   if pid:await_shutdown(saved,int(pid.group(1)))
 paths=list((ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge/storage').glob('*.class'))+[ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge/VandorLabsBootstrap.class']
 (out/'classes.sha256').write_text('\n'.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+str(p.relative_to(ROOT)) for p in sorted(paths))+'\n')
 print('Canonical Storage write/restart PASS: '+str(out),flush=True)
if __name__=='__main__':main()
