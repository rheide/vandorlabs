#!/usr/bin/env python3
"""Run the isolated native matter server restart checks, optionally with a live client."""
import argparse
import os
from pathlib import Path
import signal
import socket
import subprocess
import sys
import tempfile
import time

PLATFORM = Path(__file__).resolve().parent
ROOT = PLATFORM.parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from check_portable_boundary import jdk_home


def command(task, *properties):
    wrapper = PLATFORM / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
    arguments = [str(wrapper), task, '--no-daemon', *properties]
    if os.name == 'nt':
        # cmd.exe needs raw embedded quotes; list2cmdline uses incompatible
        # backslash escaping when this command is passed as a list.
        shell = os.environ.get('COMSPEC', 'cmd.exe')
        return ('"' + shell + '" /d /v:off /s /c "'
                + ' '.join('"' + argument + '"' for argument in arguments) + '"')
    return arguments


def stop(process):
    if process.poll() is not None:
        return
    if os.name == 'nt':
        process.send_signal(signal.CTRL_BREAK_EVENT)
    else:
        os.killpg(process.pid, signal.SIGINT)
    try:
        process.wait(timeout=30)
    except subprocess.TimeoutExpired:
        if os.name == 'nt':
            subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], check=False, capture_output=True)
        else:
            os.killpg(process.pid, signal.SIGTERM)
        process.wait(timeout=10)


def launch(args, log, env):
    output = log.open('w')
    options = {'creationflags': subprocess.CREATE_NEW_PROCESS_GROUP} if os.name == 'nt' else {'start_new_session': True}
    try:
        return subprocess.Popen(args, cwd=PLATFORM, env=env, stdout=output, stderr=subprocess.STDOUT, **options)
    finally:
        output.close()


def check_log(log, marker):
    text = log.read_text(errors='replace')
    if marker not in text or any(value in text for value in [
        'Encountered an unexpected exception', 'Crash report saved', 'Native matter:', 'Native matter client:']):
        raise RuntimeError('Required clean PASS missing: ' + str(log))


def run(args, log, marker, env):
    process = launch(args, log, env)
    try:
        result = process.wait(timeout=180)
        if result:
            raise RuntimeError('Nonzero exit %d: %s' % (result, log))
        check_log(log, marker)
    finally:
        stop(process)
    print(marker + ' (' + str(log.relative_to(ROOT)) + ')', flush=True)


def world(directory, port):
    directory.mkdir(parents=True)
    (directory / 'eula.txt').write_text('eula=true\n')
    (directory / 'server.properties').write_text(
        'server-ip=127.0.0.1\nserver-port=%d\nonline-mode=false\nmax-tick-time=0\nview-distance=2\nsimulation-distance=2\n' % port)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', action='store_true', help='Also launch a real native client and capture a screenshot')
    parser.add_argument('--xvfb', action='store_true', help='Run the client on an isolated Linux Xvfb display with software GL')
    args = parser.parse_args()
    if args.xvfb and (not args.client or os.name == 'nt'):
        parser.error('--xvfb requires --client on Linux')
    env = dict(os.environ, JAVA_HOME=str(jdk_home(21)))
    base = ROOT / 'build/test-artifacts'
    base.mkdir(parents=True, exist_ok=True)
    output = Path(tempfile.mkdtemp(prefix='matter-native-', dir=str(base)))
    saved = output / 'saved-world'
    world(saved, 0)
    for phase in ['write', 'read']:
        run(command('runServer', '-PmodernServerRunDirectory=' + str(saved), '-PmatterPhase=' + phase),
            output / (phase + '.log'), '[vandorlabs][matter] server-' + phase + ' PASS', env)
        check_log(saved / 'logs/latest.log', 'All dimensions are saved')
    if not args.client:
        return
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        port = listener.getsockname()[1]
    server_world = output / 'client-server'
    world(server_world, port)
    server_log = output / 'client-server.log'
    server = launch(command('runServer', '-PmodernServerRunDirectory=' + str(server_world), '-PmatterPhase=client'), server_log, env)
    try:
        deadline = time.monotonic() + 120
        while 'Done (' not in server_log.read_text(errors='replace'):
            if server.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError('Client fixture server did not start: ' + str(server_log))
            time.sleep(0.5)
        client = output / 'client'
        client.mkdir()
        (client / 'options.txt').write_text('guiScale:1\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
        launch_client = command('runClient', '-PmatterPhase=client', '-PmatterClientPort=' + str(port), '-PmodernClientRunDirectory=' + str(client))
        if args.xvfb:
            launch_client = ['xvfb-run', '-a', '-s', '-screen 0 1280x720x24'] + launch_client
            env['LIBGL_ALWAYS_SOFTWARE'] = '1'
        run(launch_client, output / 'client.log', '[vandorlabs][matter] client PASS', env)
        screenshot = client / 'screenshots/matter-native.png'
        if not screenshot.is_file():
            raise RuntimeError('Screenshot missing: ' + str(screenshot))
        from PIL import Image
        image = Image.open(screenshot).convert('RGB')
        for index, name in enumerate(['block', 'matter ingot', 'alloy ingot']):
            x = 20 + 24 * index
            slot = image.crop((x, 20, x + 18, 38))
            background = (33, 56, 75)
            edges = [slot.getpixel((column, row)) for column in range(18) for row in range(18)
                     if column in [0, 17] or row in [0, 17]]
            if any(pixel != background for pixel in edges):
                raise RuntimeError('Rendered icon exceeds padded slot: ' + name)
            if all(slot.getpixel((column, row)) == background for column in range(18) for row in range(18)):
                raise RuntimeError('Rendered icon missing: ' + name)
        print('PASS native rendered icon padding, all three variants', flush=True)
        print('Inspect native screenshot: ' + str(screenshot.relative_to(ROOT)), flush=True)
    finally:
        stop(server)
    check_log(server_world / 'logs/latest.log', 'Stopping server')
    check_log(server_world / 'logs/latest.log', 'All dimensions are saved')
    check_log(server_log, '[vandorlabs][matter] client fixture READY')
    check_log(server_log, '[vandorlabs][matter] server-client-actions PASS')
    print('Development launch checks passed; this is not exact-JAR or hardware acceptance.', flush=True)


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.TimeoutExpired) as error:
        print('Matter smoke failed: ' + str(error), file=sys.stderr)
        sys.exit(1)
