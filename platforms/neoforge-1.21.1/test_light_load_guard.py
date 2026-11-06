#!/usr/bin/env python3
"""Focused native Light original platform-data retention and superclass failure recovery."""
import os,hashlib
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='light-load-guard-',dir=base));saved=out/'saved-world';world(saved,0)
 for phase in ['write','read']:
  run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=light-load-guard-'+phase),out/(phase+'.log'),'[vandorlabs][light-load-guard] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
  check_log(saved/'logs/latest.log','All dimensions are saved')
 classes=ROOT/'platforms/neoforge-1.21.1/build/classes/java/main';paths=sorted((classes/'com/vandorlabs/neoforge/light').glob('NativeLight*.class'))+sorted((classes/'com/vandorlabs/neoforge').glob('NativePlatformComponents*.class'))+sorted((classes/'com/vandorlabs/neoforge/mixin').glob('NativeAttachment*.class'))
 (out/'classes.sha256').write_text(''.join(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+str(path.relative_to(classes))+'\n' for path in paths))
 print('Native Light load guard focused PASS: '+str(out),flush=True)
if __name__=='__main__':main()
