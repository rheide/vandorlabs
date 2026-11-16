#!/usr/bin/env python3
"""Native solar infuser recipes, physical battery charging and saved-process continuation."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 out=Path(artifact_directory(prefix='infuser-runtime-',dir=ROOT/'build/test-artifacts'))
 base=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.infuserRuntimeChecks=true')
 for phase in ('runtime','save','read'):
  saved=out/('runtime-world' if phase=='runtime' else 'restart-world')
  if phase!='read':fixture_world(saved,0)
  env=dict(base,JAVA_TOOL_OPTIONS=base['JAVA_TOOL_OPTIONS']+' -Dvandorlabs.infuserRuntimePhase='+phase)
  run(command('runServer','-PmodernServerRunDirectory='+str(saved)),out/(phase+'.log'),'[vandorlabs][infuser-runtime] '+phase+' PASS',env)
 print('Native Matter Infuser runtime and cold saved process PASS '+str(out),flush=True)
if __name__=='__main__':main()
