#!/usr/bin/env python3
"""Canonical seven-role configured Screen ownership through actual item use/mining and restart."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
    reference=ROOT/'build/test-artifacts/screen-configured-placement-reference.json'
    if not reference.is_file():raise RuntimeError('Export actual Source configured placement reference before creating a runtime')
    out=Path(artifact_directory(prefix='screen-canonical-items-native-',dir=ROOT/'build/test-artifacts'));saved=out/'saved-world';world(saved,0)
    for phase in ['write','read']:
        run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=screen-canonical-items-'+phase,'-PscreenConfiguredPlacementReference='+str(reference)),out/(phase+'.log'),'[vandorlabs][screen-canonical-items] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
        check_log(saved/'logs/latest.log','All dimensions are saved')
    print('Canonical Native Screen items PASS: '+str(out),flush=True)
if __name__=='__main__':main()
