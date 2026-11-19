#!/usr/bin/env python3
"""Check held and mounted native armor through survival Configurizer sessions."""
import argparse
import os
from pathlib import Path
import socket
import time

from native_test_artifacts import artifact_directory, retain_artifacts
from test_cube import ROOT, check_log, command, jdk_home, launch, run, stop
from test_sampled_materials import fixture_world


@retain_artifacts
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--xvfb', action='store_true')
    args = parser.parse_args()
    env = dict(os.environ, JAVA_HOME=str(jdk_home(21)),
               JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS', '') + ' -Dvandorlabs.armorClient=true')
    base = ROOT / 'build/test-artifacts'
    base.mkdir(parents=True, exist_ok=True)
    output = Path(artifact_directory(prefix='armor-client-', dir=base))
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        port = listener.getsockname()[1]
    server_dir = output / 'server'
    fixture_world(server_dir, port)
    server_log = output / 'server.log'
    server = launch(command('runServer', '-PmodernServerRunDirectory=' + str(server_dir)), server_log, env)
    try:
        deadline = time.monotonic() + 140
        while 'Done (' not in server_log.read_text(errors='replace'):
            if server.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError('Armor fixture server startup failed: ' + str(server_log))
            time.sleep(.5)
        client_dir = output / 'client'
        client_dir.mkdir()
        (client_dir / 'options.txt').write_text(
            'guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\n'
            'maxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
        client = command('runClient', '-PmatterClientPort=' + str(port),
                         '-PmodernClientRunDirectory=' + str(client_dir))
        if args.xvfb:
            client = ['xvfb-run', '-a', '-s', '-screen 0 1280x720x24'] + client
            env['LIBGL_ALWAYS_SOFTWARE'] = '1'
        run(client, output / 'client.log', '[vandorlabs][armor] client PASS', env)
        deadline = time.monotonic() + 30
        while '[vandorlabs][armor] server PASS' not in server_log.read_text(errors='replace'):
            if server.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError('Armor fixture server check failed: ' + str(server_log))
            time.sleep(.1)
    finally:
        stop(server)
    check_log(server_log, '[vandorlabs][armor] server PASS')
    print('Native armor client/server PASS: ' + str(output), flush=True)


if __name__ == '__main__':
    main()
