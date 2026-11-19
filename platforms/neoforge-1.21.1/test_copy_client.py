#!/usr/bin/env python3
"""Exercise the native Copy Controller with a Survival client and dedicated server."""
import argparse
import os
from test_transmitter import command, jdk_home, run

from connected_player_fixture import run_connected_player
from native_test_artifacts import retain_artifacts


@retain_artifacts
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--xvfb', action='store_true')
    parser.add_argument('--navigation-table', action='store_true')
    parser.add_argument('--joined-navigation-table', action='store_true')
    parser.add_argument('--with-jei', action='store_true', help='Check the installed JEI catalog and search')
    options = parser.parse_args()
    if options.xvfb and os.name == 'nt':
        parser.error('--xvfb requires Linux')

    properties = ['vandorlabs.copyPlayerCheck=true']
    gradle = []
    markers = []
    if options.navigation_table or options.joined_navigation_table:
        properties.append('vandorlabs.copyPlayerNavigationCheck=true')
    if options.joined_navigation_table:
        properties.append('vandorlabs.copyPlayerJoinedNavigationCheck=true')
    if options.with_jei:
        gradle.append('-PwithJei')
        properties.extend(('vandorlabs.copyPlayerJeiCheck=true', 'vandorlabs.telescopicLanding=true'))
        markers.append('[vandorlabs][copy-player] JEI catalog PASS')
    out = run_connected_player('copy', options.xvfb, properties, gradle, markers)
    if options.joined_navigation_table:
        env = dict(os.environ, JAVA_HOME=str(jdk_home(21)))
        env['JAVA_TOOL_OPTIONS'] = env.get('JAVA_TOOL_OPTIONS', '') + (
            ' -Dvandorlabs.copyPlayerJoinedNavigationCheck=true -Dvandorlabs.copyPlayerNavigationReload=true')
        run(command('runServer', '-PmodernServerRunDirectory=' + str(out / 'server')),
            out / 'reload.log', '[vandorlabs][copy-player] Navigation reload PASS', env)
    print('Native Copy Controller player PASS: ' + str(out), flush=True)


if __name__ == '__main__':
    main()
