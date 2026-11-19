#!/usr/bin/env python3
"""Three native builders: real asynchronous formation, powered processing and durable Survival mining."""
import os
import argparse
from pathlib import Path
from test_transmitter import ROOT,command,run,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
from navigation_test_artifact import snapshot
@retain_artifacts
def main():
    parser=argparse.ArgumentParser()
    focus=parser.add_mutually_exclusive_group()
    focus.add_argument('--production-only',action='store_true')
    focus.add_argument('--gantry-only',action='store_true')
    args=parser.parse_args()
    out=Path(artifact_directory(prefix='builder-runtime-',dir=ROOT/'build/test-artifacts'));saved=out/'saved-world';fixture_world(saved,0);frozen=snapshot(out,'before')
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.builders=true -Dvandorlabs.moduleRack=true -Dvandorlabs.builderRuntimeChecks=true -Dvandorlabs.cellJournalChecks=true')
    if args.production_only:env['JAVA_TOOL_OPTIONS']+=' -Dvandorlabs.builderProductionOnly=true'
    if args.gantry_only:env['JAVA_TOOL_OPTIONS']+=' -Dvandorlabs.builderRecoveryPhase=gantry'
    run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-x','compileJava','-x','compileSmokeJava','-x','processResources'),out/'server.log','[vandorlabs][builder-runtime] PASS',env)
    if args.production_only:
        cold=out/'server';fixture_world(cold,0)
        for phase in ('export-save','export-read'):
            phase_env=dict(env,JAVA_TOOL_OPTIONS=env['JAVA_TOOL_OPTIONS']+' -Dvandorlabs.builderRecoveryPhase='+phase)
            run(command('runServer','-PmodernServerRunDirectory='+str(cold),'-x','compileJava','-x','compileSmokeJava','-x','processResources'),out/(phase+'.log'),'[vandorlabs][builder-runtime] '+phase+' PASS',phase_env)
    if args.gantry_only:
        disabled=out/'disabled-world';fixture_world(disabled,0)
        disabled_env=dict(env,JAVA_TOOL_OPTIONS=env['JAVA_TOOL_OPTIONS']
                          +' -Dvandorlabs.gearRuntime=false -Dvandorlabs.moduleRack=false')
        run(command('runServer','-PmodernServerRunDirectory='+str(disabled),'-x','compileJava','-x','compileSmokeJava','-x','processResources'),
            out/'disabled.log','[vandorlabs][builder-runtime] PASS disabled parts',disabled_env)
    snapshot(out,'after',frozen,require_source_unchanged=False)
    print('Native three-builder runtime PASS '+str(out),flush=True)
if __name__=='__main__':main()
