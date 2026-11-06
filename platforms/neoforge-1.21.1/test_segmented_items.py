#!/usr/bin/env python3
"""Inactive Segmented configured-item placement, source defaults, ownership faults and restart."""
import argparse,os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 p=argparse.ArgumentParser();p.add_argument('--legacy-reference',type=Path,required=True);a=p.parse_args();reference=a.legacy_reference.resolve()
 if not reference.is_file():p.error('exportSegmentedItemReference output required')
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='segmented-item-native-',dir=base));saved=out/'saved-world';world(saved,0)
 pack=saved/'world/datapacks/segmented-item-fixture';pack.mkdir(parents=True);(pack/'pack.mcmeta').write_text('{"pack":{"pack_format":48,"description":"Segmented item fixture tags"}}')
 for name in ['mineable/pickaxe','needs_stone_tool']:
  tag=pack/('data/minecraft/tags/block/'+name+'.json');tag.parent.mkdir(parents=True,exist_ok=True);tag.write_text('{"replace":false,"values":[{"id":"vandorlabs:segmented_item_fixture","required":false}]}')
 for phase in ['write','read']:
  run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=segmented-item-'+phase,'-PsegmentedItemReference='+str(reference)),out/(phase+'.log'),'[vandorlabs][segmented-item] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
  check_log(saved/'logs/latest.log','All dimensions are saved')
 print('Native Segmented configured-item focused checks PASS: '+str(out),flush=True)
if __name__=='__main__':main()
