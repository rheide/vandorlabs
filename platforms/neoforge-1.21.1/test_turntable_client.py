#!/usr/bin/env python3
"""Exercise opt-in Turntable placement bindings, menus, and actual client restore."""
import argparse
import os

from connected_player_fixture import run_connected_player
from native_test_artifacts import retain_artifacts


@retain_artifacts
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--xvfb', action='store_true')
    options = parser.parse_args()
    if options.xvfb and os.name == 'nt':
        parser.error('--xvfb requires Linux')
    out = run_connected_player('turntable', options.xvfb,
                               ('vandorlabs.turntableControllers=true', 'vandorlabs.turntablePlayerChecks=true'))
    print('Native Turntable player PASS: ' + str(out), flush=True)


if __name__ == '__main__':
    main()
