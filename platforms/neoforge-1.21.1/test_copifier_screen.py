#!/usr/bin/env python3
"""Canonical copied Screen leaf commits and complete-process restart."""
import os,hashlib
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
 output=Path(artifact_directory(prefix='copifier-screen-native-',dir=base));saved=output/'saved-world';world(saved,0)
 for phase in ['write','read']:
  run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=copifier-screen-'+phase,'-PcopifierScreenReference='+str(ROOT/'testclient/fixtures/copifier-screen-source.jsonl.gz')),output/(phase+'.log'),'[vandorlabs][copifier-screen] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
  check_log(saved/'logs/latest.log','All dimensions are saved')
 evidence=[]
 for path in sorted((ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge/screen').glob('NativeScreen*.class')):
  evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name)
 for path in sorted((ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge/copifier').glob('NativeCopifierScreen*.class')):
  evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name)
 source=ROOT/'testclient/fixtures/copifier-screen-source.jsonl.gz'
 evidence.append(hashlib.sha256(source.read_bytes()).hexdigest()+'  '+source.name)
 (output/'classes-and-reference.sha256').write_text('\n'.join(evidence)+'\n')
 print('Native copied Screen focused PASS: '+str(output),flush=True)
if __name__=='__main__':main()
