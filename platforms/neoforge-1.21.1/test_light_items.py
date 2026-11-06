#!/usr/bin/env python3
"""Private Light configured items and whole-process restart."""
import os,hashlib
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
 output=Path(artifact_directory(prefix='light-item-native-',dir=base));saved=output/'saved-world';world(saved,0)
 pack=saved/'world/datapacks/light-fixture-tools';pack.mkdir(parents=True);(pack/'pack.mcmeta').write_text('{"pack":{"pack_format":48,"description":"Light fixture harvest tools"}}')
 for name in ['mineable/pickaxe','needs_stone_tool']:
  tag=pack/('data/minecraft/tags/block/'+name+'.json');tag.parent.mkdir(parents=True,exist_ok=True);tag.write_text('{"replace":false,"values":['+','.join('{"id":"vandorlabs:light_item_fixture_'+str(i)+'","required":false}' for i in range(3))+']}')
 for phase in ['write','read']:
  run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=light-item-'+phase,'-PlightPlacementReference='+str(ROOT/'build/test-artifacts/light-placement-forge.jsonl')),output/(phase+'.log'),'[vandorlabs][light-item] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
  check_log(saved/'logs/latest.log','All dimensions are saved')
 evidence=[]
 for path in sorted((ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge/light').glob('NativeLight*.class')):
  evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name)
 for name in ['light-geometry-forge.jsonl','programmable-light-settings-forge.jsonl','light-joining-forge.jsonl','light-placement-forge.jsonl']:
  path=ROOT/'build/test-artifacts'/name
  if path.is_file():evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+name)
 (output/'classes-and-reference.sha256').write_text('\n'.join(evidence)+'\n')
 print('Native Light item focused PASS: '+str(output),flush=True)
if __name__=='__main__':main()
