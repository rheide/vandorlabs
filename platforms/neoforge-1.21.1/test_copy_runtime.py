#!/usr/bin/env python3
"""One native Copy Controller async paired-pad material ownership path."""
import argparse
import os
from pathlib import Path
from test_transmitter import ROOT, command, run, jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--paired-chest', action='store_true')
    options = parser.parse_args()
    label = 'copy-paired-chest' if options.paired_chest else 'copy-runtime'
    out=Path(artifact_directory(prefix=label+'-',dir=ROOT/'build/test-artifacts'))
    world=out/'saved-world'
    fixture_world(world,0)
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.copyRuntimeChecks=true')
    if options.paired_chest:
        env['JAVA_TOOL_OPTIONS'] += ' -Dvandorlabs.copyPairedChestCheck=true'
    run(command('runServer','-PmodernServerRunDirectory='+str(world)),out/'server.log',f'[vandorlabs][{label}] PASS',env)
    print('Native Copy Controller runtime PASS '+str(out),flush=True)
if __name__=='__main__':main()
