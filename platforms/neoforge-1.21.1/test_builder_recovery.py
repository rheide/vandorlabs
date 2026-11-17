#!/usr/bin/env python3
"""Abrupt real-ARMED formation and mining interruption, each with one saved chunk and cold rollback."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
from navigation_test_artifact import snapshot
@retain_artifacts
def main():
    out=Path(artifact_directory(prefix='builder-recovery-',dir=ROOT/'build/test-artifacts'))
    frozen=snapshot(out,'before')
    base=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.builders=true -Dvandorlabs.moduleRack=true -Dvandorlabs.builderRuntimeChecks=true -Dvandorlabs.cellJournalChecks=true')
    for kind in ('form','mine','owned'):
        saved=out/'saved-world'/kind;fixture_world(saved,0)
        for phase in ('crash-'+kind,'read-'+kind):
            env=dict(base,JAVA_TOOL_OPTIONS=base['JAVA_TOOL_OPTIONS']+' -Dvandorlabs.builderRecoveryPhase='+phase)
            run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-x','compileJava','-x','compileSmokeJava','-x','processResources'),out/(phase+'.log'),'[vandorlabs][builder-runtime] '+phase+' PASS',env)
    snapshot(out,'after',frozen,require_source_unchanged=False)
    print('Native builder abrupt partial-save recovery PASS '+str(out),flush=True)
if __name__=='__main__':main()
