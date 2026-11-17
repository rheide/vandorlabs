#!/usr/bin/env python3
"""Native compact navigation persistence, wire bounds/cache and real powered Assembly Press."""
import os,argparse
from pathlib import Path
from test_transmitter import ROOT,command,run,jdk_home
from test_sampled_materials import fixture_world
from navigation_test_artifact import snapshot
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--allow-working-source-changes',action='store_true',help='Keep compiled JAR/runtime frozen while recording independent uncompiled source edits');options=parser.parse_args()
 out=Path(artifact_directory(prefix='navigation-data-',dir=ROOT/'build/test-artifacts'));saved=out/'saved-world';fixture_world(saved,0);frozen=snapshot(out,'before')
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.navigationDataChecks=true')
 run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-x','compileJava','-x','compileSmokeJava','-x','processResources'),out/'server.log','[vandorlabs][navigation-data] PASS',env)
 snapshot(out,'after',frozen,require_source_unchanged=not options.allow_working_source_changes)
 print('Native navigation data PASS '+str(out),flush=True)
if __name__=='__main__':main()
