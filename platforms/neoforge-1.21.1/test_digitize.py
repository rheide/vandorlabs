#!/usr/bin/env python3
"""Native completion-owned Digitize work, actual cold partial save and exact dynamic manufacturing."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,jdk_home
from test_sampled_materials import fixture_world
from navigation_test_artifact import snapshot
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 out=Path(artifact_directory(prefix='digitize-',dir=ROOT/'build/test-artifacts'));saved=out/'saved-world';fixture_world(saved,0);frozen=snapshot(out,'before')
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.digitizeChecks=true')
 for phase in ('save','read'):
  phase_env=dict(env,JAVA_TOOL_OPTIONS=env['JAVA_TOOL_OPTIONS']+' -Dvandorlabs.digitizePhase='+phase)
  run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-x','compileJava','-x','compileSmokeJava','-x','processResources'),out/(phase+'.log'),'[vandorlabs][digitize] '+phase+' PASS',phase_env)
 snapshot(out,'after',frozen,require_source_unchanged=False)
 print('Native Digitize PASS '+str(out),flush=True)
if __name__=='__main__':main()
