"""An isolated dedicated server and one real client for controller interactions."""
import os
from pathlib import Path
import shutil
import socket
import subprocess
import time

from native_test_artifacts import artifact_directory
from test_cube import ROOT, check_log, command, jdk_home, launch, stop, world
from test_sampled_materials import fixture_world


def run_connected_player(scene, xvfb, properties, gradle=(), client_markers=(), saved_world=None, world_seed=None):
    env = dict(os.environ, JAVA_HOME=str(jdk_home(21)))
    env['JAVA_TOOL_OPTIONS'] = env.get('JAVA_TOOL_OPTIONS', '') + ''.join(' -D' + prop for prop in properties)
    base = ROOT / 'build/test-artifacts'
    base.mkdir(parents=True, exist_ok=True)
    out = Path(artifact_directory(prefix=scene + '-player-', dir=base))
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        port = listener.getsockname()[1]
    saved = out / 'server'
    if world_seed is None:
        fixture_world(saved, port)
    else:
        world(saved, port)
        with (saved / 'server.properties').open('a') as properties_file:
            properties_file.write(f'level-type=minecraft:normal\ngenerate-structures=false\nlevel-seed={world_seed}\n')
    if saved_world is not None:
        shutil.copytree(saved_world, saved / 'world')
    server_log = out / 'server.log'
    server = launch(command('runServer', '-PmodernServerRunDirectory=' + str(saved), *gradle), server_log, env)
    try:
        deadline = time.monotonic() + 120
        while 'Done (' not in server_log.read_text(errors='replace'):
            if server.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError(f'Native {scene} server startup: {server_log}')
            time.sleep(.5)
        client = out / 'client'
        client.mkdir()
        (client / 'options.txt').write_text(
            'guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\n'
            'maxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
        client_log = out / 'client.log'
        args = command('runClient', '-PmatterClientPort=' + str(port), '-PmodernClientRunDirectory=' + str(client), *gradle)
        if xvfb:
            args = ['xvfb-run', '-a', '-s', '-screen 0 1280x720x24'] + args
            env = dict(env, LIBGL_ALWAYS_SOFTWARE='1')
        process = launch(args, client_log, env)
        try:
            deadline = time.monotonic() + 240
            while process.poll() is None:
                if server.poll() is not None:
                    raise RuntimeError(f'Native {scene} server stopped during the client check: {server_log}')
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise RuntimeError(f'Native {scene} client timed out: {client_log}')
                try:
                    process.wait(timeout=min(1, remaining))
                except subprocess.TimeoutExpired:
                    continue
            if process.returncode:
                raise RuntimeError(f'Native {scene} client failed: {client_log}')
            for marker in (f'[vandorlabs][{scene}-player] client PASS', *client_markers):
                check_log(client_log, marker)
        finally:
            stop(process)
        check_log(server_log, f'[vandorlabs][{scene}-player] server PASS')
    finally:
        stop(server)

    saved_log = saved / 'logs/latest.log'
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline and 'All dimensions are saved' not in saved_log.read_text(errors='replace'):
        time.sleep(.1)
    check_log(saved_log, 'All dimensions are saved')
    return out
