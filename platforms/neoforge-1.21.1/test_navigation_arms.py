#!/usr/bin/env python3
"""Cross-chunk joined navigation table module/pin ownership through real arms and cold reversal."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
from navigation_test_artifact import snapshot
from contextlib import contextmanager
@contextmanager
def frozen(out):
 baseline=snapshot(out,"before")
 try:yield
 finally:snapshot(out,"after",baseline)
@retain_artifacts
def main():
 out=Path(artifact_directory(prefix='navigation-arms-',dir=ROOT/'build/test-artifacts'));saved=out/'world';fixture_world(saved,0)
 with frozen(out):
  for phase in ('save','read'):
   env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.navigationArmChecks=true -Dvandorlabs.navigationArmPhase='+phase)
   run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-x','compileJava','-x','compileSmokeJava','-x','processResources'),out/(phase+'.log'),'[vandorlabs][navigation-arm] '+phase+' PASS',env)
 print('Native navigation arms PASS '+str(out),flush=True)
if __name__=='__main__':main()
