#!/usr/bin/env python3
"""Private actual configured Screen item transactions and complete process restart."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
    out=Path(artifact_directory(prefix='screen-items-native-',dir=ROOT/'build/test-artifacts'));saved=out/'saved-world';world(saved,0)
    pack=saved/'world/datapacks/screen-fixture-tools';pack.mkdir(parents=True);(pack/'pack.mcmeta').write_text('{"pack":{"pack_format":48,"description":"Screen configured harvest tools"}}')
    for name in ['mineable/pickaxe','needs_stone_tool']:
        tag=pack/('data/minecraft/tags/block/'+name+'.json');tag.parent.mkdir(parents=True,exist_ok=True);tag.write_text('{"replace":false,"values":['+','.join('{"id":"vandorlabs:screen_item_world_'+str(index)+'","required":false}' for index in range(7))+']}')
    for phase in ['write','read']:
        run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=screen-item-world-'+phase,'-PscreenConfiguredPlacementReference='+str(ROOT/'build/test-artifacts/screen-configured-placement-reference.json')),out/(phase+'.log'),'[vandorlabs][screen-item-world] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
        check_log(saved/'logs/latest.log','All dimensions are saved')
    print('Native Screen items focused PASS: '+str(out),flush=True)
if __name__=='__main__':main()
