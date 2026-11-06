#!/usr/bin/env python3
"""Focused loaded block-entity index identity/thread/lifecycle checks."""
import os
from pathlib import Path
from test_transmitter import ROOT, command, run, world, check_log, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts


@retain_artifacts
def main():
    base = ROOT / 'build/test-artifacts'
    base.mkdir(parents=True, exist_ok=True)
    output = Path(artifact_directory(prefix='loaded-tile-index-', dir=base))
    saved = output / 'saved-world'
    world(saved, 0)
    run(command('runServer', '-PmodernServerRunDirectory=' + str(saved), '-PtransmitterPhase=loaded-tile-index'), output / 'server.log', '[vandorlabs][loaded-tile-index] dedicated PASS', dict(os.environ, JAVA_HOME=str(jdk_home(21))))
    check_log(saved / 'logs/latest.log', 'All dimensions are saved')
    print('Native loaded-tile index focused PASS: ' + str(output), flush=True)


if __name__ == '__main__':
    main()
