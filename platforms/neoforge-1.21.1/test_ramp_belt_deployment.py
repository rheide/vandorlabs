#!/usr/bin/env python3
"""Actual Source deployment geometry through native body publication and codec."""
import hashlib,os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 output=Path(artifact_directory(prefix='ramp-belt-deployment-native-',dir=ROOT/'build/test-artifacts'));saved=output/'world';world(saved,0)
 try:
  run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=ramp-belt-deployment','-PrampBeltDeploymentReference='+str(ROOT/'testclient/fixtures/ramp-belt-deployment-source.jsonl.gz')),output/'server.log','[vandorlabs][ramp-belt-deployment] PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
 finally:
  evidence=[]
  for tree in ['main','smoke']:
   for path in sorted((ROOT/('platforms/neoforge-1.21.1/build/classes/java/'+tree+'/com/vandorlabs/neoforge/ramp')).glob('NativeRamp*.class')):evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+tree+'/'+path.name)
  (output/'classes.sha256').write_text('\n'.join(evidence)+'\n')
 print('Native Ramp belt deployment PASS: '+str(output),flush=True)
if __name__=='__main__':main()
