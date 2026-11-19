#!/usr/bin/env python3
"""Exercise the native Copy Controller with a Survival client and dedicated server."""
import argparse
import os

from connected_player_fixture import run_connected_player
from native_test_artifacts import retain_artifacts


@retain_artifacts
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--xvfb', action='store_true')
    parser.add_argument('--navigation-table', action='store_true')
    parser.add_argument('--with-jei', action='store_true', help='Check the installed JEI catalog and search')
    options = parser.parse_args()
    if options.xvfb and os.name == 'nt':
        parser.error('--xvfb requires Linux')

    properties = ['vandorlabs.copyPlayerCheck=true']
    gradle = []
    markers = []
    if options.navigation_table:
        properties.append('vandorlabs.copyPlayerNavigationCheck=true')
    if options.with_jei:
        gradle.append('-PwithJei')
        properties.extend(('vandorlabs.copyPlayerJeiCheck=true', 'vandorlabs.telescopicLanding=true'))
        markers.append('[vandorlabs][copy-player] JEI catalog PASS')
    out = run_connected_player('copy', options.xvfb, properties, gradle, markers)
    print('Native Copy Controller player PASS: ' + str(out), flush=True)


if __name__ == '__main__':
    main()
