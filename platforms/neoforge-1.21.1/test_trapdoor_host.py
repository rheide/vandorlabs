#!/usr/bin/env python3
"""Inactive actual Trapdoor loaded controls, old links and whole-process recovery."""
import os,hashlib
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);output=Path(artifact_directory(prefix='trapdoor-host-native-',dir=base));saved=output/'saved-world';world(saved,0)
 reference=ROOT/'build/test-artifacts/trapdoor-lifecycle-forge.json'
 if not reference.is_file():raise RuntimeError('exportTrapdoorLifecycleReference required')
 (output/'reference.sha256').write_text(hashlib.sha256(reference.read_bytes()).hexdigest()+'  '+reference.name+'\n')
 for phase in ['write','read']:
  run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=trapdoor-host-'+phase,'-PtrapdoorLifecycleReference='+str(reference)),output/(phase+'.log'),'[vandorlabs][trapdoor-host] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
  check_log(saved/'logs/latest.log','All dimensions are saved')
 print('Native Trapdoor host focused PASS: '+str(output),flush=True)
if __name__=='__main__':main()
