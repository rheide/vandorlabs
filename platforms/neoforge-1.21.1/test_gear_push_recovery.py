#!/usr/bin/env python3
"""Abrupt destination-only chunk save during an actual journaled Extender push."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 out=Path(artifact_directory(prefix='gear-push-recovery-',dir=ROOT/'build/test-artifacts'));saved=out/'saved-world';fixture_world(saved,0)
 for phase in ('crash','recover'):
  env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.gearJournalPhase=push -Dvandorlabs.gearPushRecovery='+phase)
  run(command('runServer','-PmodernServerRunDirectory='+str(saved)),out/(phase+'.log'),'[vandorlabs][gear-push-recovery] '+phase+' PASS',env)
 print('Native arm push abrupt recovery PASS '+str(out),flush=True)
if __name__=='__main__':main()
