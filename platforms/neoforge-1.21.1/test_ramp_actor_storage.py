#!/usr/bin/env python3
"""Startup-only raw entity worker bridge; this does not establish actor recovery policy."""
import os,hashlib
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 output=Path(artifact_directory(prefix='ramp-actor-storage-native-',dir=ROOT/'build/test-artifacts'));saved=output/'saved-world';world(saved,0)
 (saved/'world/vandorlabs/ramp-journals').mkdir(parents=True,exist_ok=True)
 try:
  for phase in ['write','read']:
   run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=ramp-actor-storage-'+phase),output/(phase+'.log'),'[vandorlabs][ramp-actor-storage] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
 finally:
  evidence=[]
  for tree in ['main','smoke']:
   base=ROOT/'platforms/neoforge-1.21.1/build/classes/java'/tree/'com/vandorlabs/neoforge'
   for folder in ['ramp','mixin']:
    for path in sorted((base/folder).glob('NativeRamp*Actor*.class')):evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+tree+'/'+folder+'/'+path.name)
   for path in sorted((base/'mixin').glob('NativeRampEntity*Access.class')):evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+tree+'/mixin/'+path.name)
  (output/'classes.sha256').write_text('\n'.join(evidence)+'\n')
 print('Native Ramp startup raw actor storage PASS: '+str(output),flush=True)
if __name__=='__main__':main()
