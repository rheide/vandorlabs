#!/usr/bin/env python3
"""Native observers: exhaustive formation orientations and separate cold saved owner reload."""
import os,argparse
from pathlib import Path
from test_transmitter import ROOT,command,run,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
from navigation_test_artifact import snapshot
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--skip-matrix',action='store_true');parser.add_argument('--arms-only',action='store_true');args=parser.parse_args()
 out=Path(artifact_directory(prefix='navigation-observers-',dir=ROOT/'build/test-artifacts'))
 freeze=snapshot(out,'before')
 arm_phases=('arm-save','arm-read','arm-level-save','arm-level-read','arm-rotate-save','arm-rotate-read')
 phases=('far-parts',*arm_phases) if args.arms_only else (('save','read','far-parts',*arm_phases) if args.skip_matrix else ('matrix','save','read','far-parts',*arm_phases))
 for phase in phases:
  runtime='installed-server' if phase.startswith('arm-level-') else 'restored-server' if phase.startswith('arm-rotate-') else 'server' if phase.startswith('arm-') else 'matrix-server' if phase in ('matrix','far-parts') else 'saved-world'
  world=out/runtime
  if not phase.endswith('read'):fixture_world(world,0)
  env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.observerRuntimeChecks=true -Dvandorlabs.observerRuntimePhase='+phase)
  run(command('runServer','-x','compileJava','-x','compileSmokeJava','-x','processResources','-PmodernServerRunDirectory='+str(world)),out/(phase+'.log'),'[vandorlabs][observer-runtime] '+phase+' PASS',env)
  snapshot(out,'after-'+phase,freeze)
 print('Native navigation observers PASS '+str(out),flush=True)
if __name__=='__main__':main()
