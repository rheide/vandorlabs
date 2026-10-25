#!/usr/bin/env python3
"""Run the complete native client gate against an owned, disposable local fixture server."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import socket
import signal
import subprocess
import sys
import tempfile
import time

from build_all import ROOT, java_home
from test_modern_runtime import PLATFORM, run, stop_owned


def output_hashes():
    result = {}
    for folder in ('build/classes/java/main', 'build/resources/main'):
        for path in sorted((PLATFORM / folder).rglob('*')):
            if path.is_file():
                result[str(path.relative_to(PLATFORM))] = hashlib.sha256(path.read_bytes()).hexdigest()
    if not result:
        raise RuntimeError('Build the native target before running the frozen client gate')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=25578)
    parser.add_argument('--timeout', type=int, default=900)
    parser.add_argument('--log-dir', type=Path, default=ROOT / 'build/reports/modern-client')
    parser.add_argument('--legacy-shape-reference', type=Path)
    parser.add_argument('--suite', choices=['full','walls'], default='full')
    parser.add_argument('--legacy-wall-profiles', type=Path)
    parser.add_argument('--legacy-wall-reference', type=Path)
    parser.add_argument('--legacy-wall-item-models',type=Path)
    parser.add_argument('--legacy-wall-item-image',type=Path)
    parser.add_argument('--xvfb', action='store_true', help='Use an isolated virtual display, also selected automatically when DISPLAY is absent')
    args = parser.parse_args()
    if not 1024 <= args.port <= 65535 or args.timeout <= 0:
        parser.error('A nonprivileged valid port and positive timeout are required')
    # Refuse to reuse a listener; cleanup is limited to the process tree started below.
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', args.port))
    args.log_dir.mkdir(parents=True, exist_ok=True)
    args.log_dir = args.log_dir.resolve()
    args.compiled = True
    args.xvfb = args.xvfb or os.name != 'nt' and not os.environ.get('DISPLAY')
    env = dict(os.environ, JAVA_HOME=java_home(21, 'VANDOR_JAVA21_HOME'))
    env['PATH'] = str(Path(env['JAVA_HOME']) / 'bin') + os.pathsep + env.get('PATH', '')
    wrapper = PLATFORM / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
    before = output_hashes()
    (args.log_dir / 'outputs-before.json').write_text(json.dumps(before, indent=2) + '\n')
    fixture_root = PLATFORM / 'run'
    fixture_root.mkdir(parents=True, exist_ok=True)
    server = None
    result = None
    report = args.log_dir / 'result.json'
    report.write_text(json.dumps({'status': 'RUNNING'}, indent=2) + '\n')
    previous_handler = signal.getsignal(signal.SIGTERM)
    def interrupted(signum, frame):
        raise KeyboardInterrupt()
    signal.signal(signal.SIGTERM, interrupted)
    try:
        with tempfile.TemporaryDirectory(prefix='client-fixture-', dir=fixture_root) as directory:
            fixture = Path(directory).resolve()
            (fixture / 'eula.txt').write_text('eula=true\n')
            (fixture / 'server.properties').write_text(
                f'server-ip=127.0.0.1\nserver-port={args.port}\nonline-mode=false\n'
                'level-name=fixture\nlevel-type=minecraft:flat\nspawn-protection=0\n'
                'view-distance=10\nsimulation-distance=10\nenable-rcon=false\n')
            command = [str(wrapper), 'runServer', '--no-daemon', '-PmodernClientFixture=true',
                       '-PmodernServerRunDirectory=' + str(fixture),
                       '-x', 'compileJava', '-x', 'processResources', '-x', 'generateModernResources']
            if args.suite=='walls':command+=['-PmodernClientSuite=walls']
            if os.name == 'nt':
                command = [os.environ.get('COMSPEC', 'cmd.exe'), '/d', '/v:off', '/s', '/c', subprocess.list2cmdline(command)]
            server_path = args.log_dir / 'fixture-server.log'
            with server_path.open('w') as stream:
                server = subprocess.Popen(command, cwd=PLATFORM, env=env, stdout=stream,
                                          stderr=subprocess.STDOUT, start_new_session=os.name != 'nt')
                try:
                    deadline = time.monotonic() + 180
                    while 'Done (' not in server_path.read_text(errors='replace'):
                        if server.poll() is not None:
                            raise RuntimeError('Owned fixture server exited before readiness; inspect fixture-server.log')
                        if time.monotonic() > deadline:
                            raise RuntimeError('Owned fixture startup exceeded 180 seconds')
                        time.sleep(.5)
                    print('Owned disposable fixture ready; running native client suite '+args.suite, flush=True)
                    result = run('client', args)
                finally:
                    stop_owned(server)
                    server = None
    except BaseException as error:
        report.write_text(json.dumps({'status': 'FAILED', 'error': str(error)}, indent=2) + '\n')
        raise
    finally:
        signal.signal(signal.SIGTERM, previous_handler)
        if server is not None:
            stop_owned(server)
        after = output_hashes()
        (args.log_dir / 'outputs-after.json').write_text(json.dumps(after, indent=2) + '\n')
        if before != after:
            raise RuntimeError('Native compiled outputs changed during the paired client gate')
    report.write_text(json.dumps({'status': 'PASS', 'compiled_files': len(before), 'client': result}, indent=2) + '\n')
    print(f'Paired native client PASS: {len(before)} compiled outputs unchanged; owned fixture stopped and discarded', flush=True)


if __name__ == '__main__':
    main()
