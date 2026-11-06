#!/usr/bin/env python3
"""Private Propulsion editor actual-source transaction replay."""
import os,hashlib
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
 output=Path(artifact_directory(prefix='propulsion-editor-native-',dir=base));saved=output/'saved-world';world(saved,0)
 for phase in ['write']:
  run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=propulsion-editor-'+phase,'-PpropulsionShapeReference='+str(ROOT/'build/test-artifacts/propulsion-editor-forge.jsonl')),output/(phase+'.log'),'[vandorlabs][propulsion-editor] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
  check_log(saved/'logs/latest.log','All dimensions are saved')
 evidence=[]
 for path in sorted((ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge/propulsion').glob('NativePropulsion*.class')):
  evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name)
 for name in ['propulsion-editor-forge.jsonl']:
  path=ROOT/'build/test-artifacts'/name
  if path.is_file():evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+name)
 (output/'classes-and-reference.sha256').write_text('\n'.join(evidence)+'\n')
 print('Native Propulsion editor focused PASS: '+str(output),flush=True)
if __name__=='__main__':main()
