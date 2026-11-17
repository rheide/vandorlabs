#!/usr/bin/env python3
"""Bound real concurrent max observer journals to recovery capacity, then cold reload all owners."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
from navigation_test_artifact import snapshot
@retain_artifacts
def main():
 out=Path(artifact_directory(prefix='observer-directory-',dir=ROOT/'build/test-artifacts'));saved=out/'saved-world';fixture_world(saved,0);frozen=snapshot(out,'before')
 for phase in ('write','read'):
  env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.observerDirectoryChecks=true -Dvandorlabs.observerDirectoryPhase='+phase)
  run(command('runServer','-x','compileJava','-x','compileSmokeJava','-x','processResources','-PmodernServerRunDirectory='+str(saved)),out/(phase+'.log'),'[vandorlabs][observer-directory] '+phase+' PASS',env)
  snapshot(out,'after-'+phase,frozen)
 print('Observer directory capacity and cold ownership PASS '+str(out),flush=True)
if __name__=='__main__':main()
