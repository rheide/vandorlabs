#!/usr/bin/env python3
"""Exercise the legacy wheel conversion command with a connected Survival operator."""
import argparse
import os
from pathlib import Path
import socket
import time

from native_test_artifacts import artifact_directory, retain_artifacts
from test_cube import ROOT, check_log, command, jdk_home, launch, stop
from test_sampled_materials import fixture_world


@retain_artifacts
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--xvfb', action='store_true')
    options = parser.parse_args()
    if options.xvfb and os.name == 'nt':
        parser.error('--xvfb requires Linux')

    env = dict(os.environ, JAVA_HOME=str(jdk_home(21)),
               JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS', '') + ' -Dvandorlabs.telescopicLanding=true -Dvandorlabs.legacyMigrationPlayerCheck=true')
    out = Path(artifact_directory(prefix='legacy-migration-player-', dir=ROOT / 'build/test-artifacts'))
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        port = listener.getsockname()[1]
    saved = out / 'server'
    fixture_world(saved, port)
    server_log = out / 'server.log'
    server = launch(command('runServer', '-PmodernServerRunDirectory=' + str(saved)), server_log, env)
    try:
        deadline = time.monotonic() + 120
        while 'Done (' not in server_log.read_text(errors='replace'):
            if server.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError('Native legacy landing conversion server startup: ' + str(server_log))
            time.sleep(.5)
        client = out / 'client'
        client.mkdir()
        (client / 'options.txt').write_text(
            'guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\n'
            'maxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
        client_log = out / 'client.log'
        args = command('runClient', '-PmatterClientPort=' + str(port), '-PmodernClientRunDirectory=' + str(client))
        if options.xvfb:
            args = ['xvfb-run', '-a', '-s', '-screen 0 1280x720x24'] + args
            env = dict(env, LIBGL_ALWAYS_SOFTWARE='1')
        process = launch(args, client_log, env)
        try:
            if process.wait(timeout=240):
                raise RuntimeError('Native legacy landing conversion client failed: ' + str(client_log))
            check_log(client_log, '[vandorlabs][legacy-migration-player] client PASS')
        finally:
            stop(process)
        check_log(server_log, '[vandorlabs][legacy-migration-player] server PASS')
    finally:
        stop(server)

    saved_log = saved / 'logs/latest.log'
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline and 'All dimensions are saved' not in saved_log.read_text(errors='replace'):
        time.sleep(.1)
    check_log(saved_log, 'All dimensions are saved')
    print('Native legacy landing conversion player PASS: ' + str(out), flush=True)


if __name__ == '__main__':
    main()
