#!/usr/bin/env python3
"""Real-item Duplifier configuration, private buffers and settings-only Undo for three ports."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 out=Path(artifact_directory(prefix='port-copy-',dir=ROOT/'build/test-artifacts'));saved=out/'saved-world';fixture_world(saved,0)
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Xmx2G -Dvandorlabs.resourcePorts=true -Dvandorlabs.portCopyCheck=true')
 run(command('runServer','-PmodernServerRunDirectory='+str(saved)),out/'server.log','[vandorlabs][port-copy] PASS',env)
 print('Native resource-port Duplifier PASS '+str(out),flush=True)
if __name__=='__main__':main()
