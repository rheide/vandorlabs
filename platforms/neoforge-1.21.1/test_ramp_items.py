#!/usr/bin/env python3
"""Private Ramp item transactions, failure ownership and fresh-process recovery."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts

@retain_artifacts
def main():
    output=Path(artifact_directory(prefix='ramp-item-native-',dir=ROOT/'build/test-artifacts'))
    saved=output/'saved-world';world(saved,0)
    pack=saved/'world/datapacks/ramp-item-tools';pack.mkdir(parents=True)
    (pack/'pack.mcmeta').write_text('{"pack":{"pack_format":48,"description":"Ramp item fixture harvest tools"}}')
    for name in ('mineable/pickaxe','needs_stone_tool'):
        target=pack/('data/minecraft/tags/block/'+name+'.json');target.parent.mkdir(parents=True,exist_ok=True)
        target.write_text('{"replace":false,"values":["vandorlabs:ramp_item_fixture_0","vandorlabs:ramp_item_fixture_1"]}')
    for phase in ('write','read'):
        run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=ramp-item-'+phase),output/(phase+'.log'),'[vandorlabs][ramp-item] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
        check_log(saved/'logs/latest.log','All dimensions are saved')
    print('Private Ramp item write/restart PASS '+str(output),flush=True)

if __name__=='__main__':main()
