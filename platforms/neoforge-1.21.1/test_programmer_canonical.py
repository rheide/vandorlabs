#!/usr/bin/env python3
"""Public Programmer placement/harvest, complete opaque item ownership and save/restart."""
import os,json
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 source=ROOT/'testclient/fixtures/programmer-mining-forge.json';contacts=ROOT/'testclient/fixtures/programmer-contacts-forge.json';inventory=ROOT/'build/test-artifacts/programmer-inventory-forge.json'
 for path,key,count in ((source,'cases',64),(contacts,'cases',384),(inventory,'timers',86)):
  if len(json.loads(path.read_text())[key])!=count:raise AssertionError('Missing frozen Programmer source '+str(path))
 out=Path(artifact_directory(prefix='programmer-canonical-',dir=ROOT/'build/test-artifacts'));saved=out/'saved-world';world(saved,0)
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_PROGRAMMER_MINING_REFERENCE=str(source),VANDOR_PROGRAMMER_CONTACTS_REFERENCE=str(contacts),VANDOR_PROGRAMMER_INVENTORY_REFERENCE=str(inventory),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.programmerCanonicalCheck=true')
 for phase in ('write','read'):
  run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=programmer-canonical-'+phase),out/(phase+'.log'),'[vandorlabs][programmer-canonical] '+phase+' PASS',env)
  check_log(saved/'logs/latest.log','All dimensions are saved')
 print('Canonical native Programmer write/restart PASS: '+str(out),flush=True)
if __name__=='__main__':main()
