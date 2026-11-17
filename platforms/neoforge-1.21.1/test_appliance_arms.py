#!/usr/bin/env python3
"""Occupied Printer/Scanner ownership through real joined Extender journals and cold reversal."""
import os,argparse
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
 finally:
  after=snapshot(out,"after")
  for key in ("artifact","artifact_sha256","runtime_input_sha256","runtime_input_count"):
   if after[key]!=baseline[key]:raise RuntimeError("Frozen appliance arm input changed: "+key)
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--rotate',action='store_true');options=parser.parse_args()
 out=Path(artifact_directory(prefix='appliance-arms-',dir=ROOT/'build/test-artifacts'));saved=out/'world';fixture_world(saved,0)
 with frozen(out):
  for phase in ('save','read'):
   env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.applianceArmChecks=true -Dvandorlabs.applianceArmRotate='+str(options.rotate).lower()+' -Dvandorlabs.applianceArmPhase='+phase)
   run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-x','compileJava','-x','compileSmokeJava','-x','processResources'),out/(phase+'.log'),'[vandorlabs][appliance-arm] '+phase+' PASS',env)
 print('Native appliance arms PASS '+str(out),flush=True)
if __name__=='__main__':main()
