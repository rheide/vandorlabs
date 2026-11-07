#!/usr/bin/env python3
"""Canonical copied Trapdoor leaf commits and complete-process restart."""
import os,hashlib,argparse
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--groups',action='store_true');options=parser.parse_args()
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
 output=Path(artifact_directory(prefix='copifier-trapdoor-native-',dir=base));saved=output/'saved-world';world(saved,0)
 for phase in (['groups'] if options.groups else ['write','read']):
  run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=copifier-trapdoor-'+phase,'-PcopifierTrapdoorReference='+str(ROOT/'testclient/fixtures/copifier-trapdoor-source.json.gz'),'-PcopifierTrapdoorGroupReference='+str(ROOT/'testclient/fixtures/copifier-trapdoor-groups.json.gz')),output/(phase+'.log'),'[vandorlabs][copifier-trapdoor] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
  check_log(saved/'logs/latest.log','All dimensions are saved')
 evidence=[]
 for path in sorted((ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge/trapdoor').glob('NativeTrapdoor*.class')):
  evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name)
 for path in sorted((ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge/copifier').glob('NativeCopifierTrapdoor*.class')):
  evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name)
 source=ROOT/'testclient/fixtures/copifier-trapdoor-source.json.gz'
 evidence.append(hashlib.sha256(source.read_bytes()).hexdigest()+'  '+source.name)
 for name in ['ProgrammableBlockEntity.class','NativeCopiedHousingMetadata.class']:
  path=ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge'/name
  evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+name)
 (output/'classes-and-reference.sha256').write_text('\n'.join(evidence)+'\n')
 print('Native copied Trapdoor focused PASS: '+str(output),flush=True)
if __name__=='__main__':main()
