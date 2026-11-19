#!/usr/bin/env python3
"""Inactive Large Door structure against actual Forge outputs, with whole-suite restart retention."""
import argparse
import os
from pathlib import Path
from test_transmitter import ROOT, command, run, world, check_log, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    parser=argparse.ArgumentParser()
    mode=parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--legacy-reference',type=Path)
    mode.add_argument('--joins-only',action='store_true')
    args=parser.parse_args()
    if args.joins_only:
        from test_sampled_materials import fixture_world
        from navigation_test_artifact import snapshot
        (ROOT / 'build/test-artifacts').mkdir(parents=True,exist_ok=True)
        output=Path(artifact_directory(prefix='large-door-join-native-',dir=ROOT/'build/test-artifacts'))
        saved=output/'saved-world'
        fixture_world(saved,0)
        frozen=snapshot(output,'before')
        base=dict(os.environ,JAVA_HOME=str(jdk_home(21)))
        for phase in ('write','read'):
            options=base.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.largeDoorJoinChecks='+phase
            run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-x','compileJava','-x','compileSmokeJava','-x','processResources'),
                output/(phase+'.log'),'[vandorlabs][large-door-join] '+phase+' PASS',dict(base,JAVA_TOOL_OPTIONS=options))
            check_log(saved/'logs/latest.log','All dimensions are saved')
        snapshot(output,'after',frozen)
        print('Native Large Door joined live checks PASS: '+str(output),flush=True)
        return
    reference=args.legacy_reference.resolve()
    controls=reference.with_name('large-door-controls-forge.json')
    gush=reference.with_name('large-door-gush-forge.jsonl')
    if not reference.is_file():parser.error('existing exportLargeDoorReference output required')
    if not controls.is_file():parser.error('existing exportLargeDoorControlsReference output required')
    if not gush.is_file():parser.error('existing exportLargeDoorGushReference output required')
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(artifact_directory(prefix='large-door-lifecycle-native-',dir=base));saved=output/'saved-world';world(saved,0)
    for phase in ['write','read']:
        run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=large-door-lifecycle-'+phase,'-PlargeDoorReference='+str(reference),'-PlargeDoorControlsReference='+str(controls),'-PlargeDoorGushReference='+str(gush)),output/(phase+'.log'),'[vandorlabs][large-door-lifecycle] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
        check_log(saved/'logs/latest.log','All dimensions are saved')
    print('Native Large Door structural lifecycle focused checks PASS: '+str(output),flush=True)
if __name__=='__main__':main()
