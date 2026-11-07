#!/usr/bin/env python3
"""Private Storage placed-world authority and whole-process restart."""
import os,hashlib
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
 output=Path(artifact_directory(prefix='storage-host-native-',dir=base));saved=output/'saved-world';world(saved,0)
 pack=saved/'world/datapacks/storage-fixture';pack.mkdir(parents=True,exist_ok=True)
 (pack/'pack.mcmeta').write_text('{"pack":{"pack_format":48,"description":"Private Storage tool contract"}}')
 for name in ['mineable/pickaxe','needs_stone_tool']:
  tag=pack/('data/minecraft/tags/block/'+name+'.json');tag.parent.mkdir(parents=True,exist_ok=True);tag.write_text('{"replace":false,"values":[{"id":"vandorlabs:storage_host_fixture","required":false}]}')
 for phase in ['write','read']:
  run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=storage-host-'+phase),output/(phase+'.log'),'[vandorlabs][storage-host] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
  check_log(saved/'logs/latest.log','All dimensions are saved')
  if phase=='write':
   check_log(output/'write.log','[vandorlabs][storage-editor] PASS')
   check_log(output/'write.log','[vandorlabs][storage-item-envelope] PASS')
 evidence=[]
 for path in sorted((ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge/storage').glob('NativeStorage*.class')):
  evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name)
 for name in ['storage-source-reference.json','storage-items-source-reference.json','storage-duplicate-source-reference.json','storage-dialog-source-reference.json']:
  path=ROOT/'build/test-artifacts'/name
  if path.is_file():evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+name)
 (output/'classes-and-reference.sha256').write_text('\n'.join(evidence)+'\n')
 print('Native Storage host focused PASS: '+str(output),flush=True)
if __name__=='__main__':main()
