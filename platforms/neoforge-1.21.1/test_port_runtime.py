#!/usr/bin/env python3
"""Focused native resource-port transfer integration, without release gates."""
import os
from pathlib import Path
from test_transmitter import ROOT, command, run, world, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    out = Path(artifact_directory(prefix='port-runtime-', dir=ROOT/'build/test-artifacts'))
    saved = out/'saved-world'
    world(saved, 0)
    env = dict(os.environ, JAVA_HOME=str(jdk_home(21)), JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS', '')+' -Dvandorlabs.resourcePorts=true -Dvandorlabs.portRuntimeChecks=true')
    run(command('runServer', '-PmodernServerRunDirectory='+str(saved)), out/'server.log', '[vandorlabs][port-runtime] PASS', env)
    print('Native resource-port integration PASS '+str(out), flush=True)

if __name__ == '__main__':
    main()
