#!/usr/bin/env python3
"""Focused actual copied-settings recipe with canonical tool and serializer registration."""
import os
import argparse
from pathlib import Path
from test_transmitter import ROOT, command, run, world, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts
from test_sampled_materials import fixture_world

@retain_artifacts
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--configured", action="store_true")
    configured = parser.parse_args().configured
    out = Path(artifact_directory(prefix='copifier-crafting-', dir=ROOT/'build/test-artifacts'))
    saved = out/'saved-world'
    fixture_world(saved, 0)
    reference = ROOT/('testclient/fixtures/copifier-recipe-configured-source.json.gz' if configured else 'testclient/fixtures/copifier-recipe-source.json.gz')
    run(command('runServer', '-PmodernServerRunDirectory='+str(saved), '-PtransmitterPhase=copifier-crafting', '-PcopifierRecipeReference='+str(reference), '-PcopifierRecipeConfigured='+str(configured).lower()), out/'server.log', '[vandorlabs][copifier-crafting] PASS', dict(os.environ, JAVA_HOME=str(jdk_home(21))))
    print('Native copied recipe PASS: '+str(out), flush=True)

if __name__ == '__main__':
    main()
