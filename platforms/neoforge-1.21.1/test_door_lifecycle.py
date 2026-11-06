#!/usr/bin/env python3
"""Placed server-player/linked-menu lifecycle; authored geometry and client dialogs remain separate."""
import argparse
import os
from pathlib import Path
from test_transmitter import ROOT, command, run, world, check_log, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--legacy-reference',type=Path,required=True);parser.add_argument('--gush-reference',type=Path);args=parser.parse_args();reference=args.legacy_reference.resolve();gush=(args.gush_reference or reference.with_name('door-gush-forge.jsonl')).resolve()
    if not reference.is_file():parser.error('existing exportDoorLifecycleReference output required')
    if not gush.is_file():parser.error('existing exportDoorGushReference output required')
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(artifact_directory(prefix='door-lifecycle-native-',dir=base));saved=output/'saved-world';world(saved,0)
    for phase in ['write','read']:
        run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=door-lifecycle-'+phase,'-PdoorLifecycleReference='+str(reference),'-PdoorGushReference='+str(gush)),output/(phase+'.log'),'[vandorlabs][door-lifecycle] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
        check_log(saved/'logs/latest.log','All dimensions are saved')
    print('Native ordinary door lifecycle focused checks PASS: '+str(output),flush=True)
if __name__=='__main__':main()
