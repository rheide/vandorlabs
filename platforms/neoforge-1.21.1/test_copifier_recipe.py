#!/usr/bin/env python3
"""Focused actual copied-settings recipe with private tool and serializer registration."""
import os
from pathlib import Path
from test_transmitter import ROOT, command, run, world, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    out = Path(artifact_directory(prefix='copifier-crafting-', dir=ROOT/'build/test-artifacts'))
    saved = out/'saved-world'
    world(saved, 0)
    reference = ROOT/'testclient/fixtures/copifier-recipe-source.json.gz'
    run(command('runServer', '-PmodernServerRunDirectory='+str(saved), '-PtransmitterPhase=copifier-crafting', '-PcopifierRecipeReference='+str(reference)), out/'server.log', '[vandorlabs][copifier-crafting] PASS', dict(os.environ, JAVA_HOME=str(jdk_home(21))))
    print('Native copied recipe PASS: '+str(out), flush=True)

if __name__ == '__main__':
    main()
