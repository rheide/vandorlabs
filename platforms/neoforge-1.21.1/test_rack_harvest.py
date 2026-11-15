#!/usr/bin/env python3
"""One abrupt cross-chunk Rack capture recovery, then its existing fractional harvest."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,jdk_home,check_log
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 out=Path(artifact_directory(prefix='rack-harvest-',dir=ROOT/'build/test-artifacts'));saved=out/'saved-world';fixture_world(saved,0)
 for phase in ['crash','recover']:
  env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.moduleRack=true -Dvandorlabs.rackHarvestChecks=true -Dvandorlabs.gearJournalPhase=rack-capture -Dvandorlabs.rackHarvestPhase='+phase)
  run(command('runServer','-PmodernServerRunDirectory='+str(saved)),out/(phase+'.log'),'[vandorlabs][rack-harvest] '+phase+' PASS',env)
  if phase=='crash':check_log(out/(phase+'.log'),'realAsyncDurableARMED/afterSourceCell/sourceOnlyCrossChunkSave/zeroLooseModules/abruptHalt')
 print('Native Rack abrupt capture recovery and captured module ownership PASS '+str(out),flush=True)
if __name__=='__main__':main()
