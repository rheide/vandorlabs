#!/usr/bin/env python3
"""Run focused native handheld/channel adapters across three independent server processes."""
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
from native_test_artifacts import artifact_directory, retain_artifacts, track_process


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
        process = subprocess.Popen(args, cwd=PLATFORM, env=env, stdout=output, stderr=subprocess.STDOUT, **options)
        try:
            return track_process(process, log)
        except BaseException:
            stop(process)
            raise
    finally:
        output.close()


def check_log(log, marker):
    text = log.read_text(errors='replace')
    if marker not in text or any(value in text for value in [
        'Encountered an unexpected exception', 'Crash report saved', 'Native transmitter:']):
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


@retain_artifacts
def main():
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)))
    base=ROOT/'build/test-artifacts'
    base.mkdir(parents=True,exist_ok=True)
    output=Path(artifact_directory(prefix='transmitter-native-',dir=str(base)))
    saved=output/'saved-world'
    world(saved,0)
    for phase in ['write','read','off']:
        run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+phase),output/(phase+'.log'),'[vandorlabs][transmitter] server-'+phase+' PASS',env)
        check_log(saved/'logs/latest.log','All dimensions are saved')
    print('Native transmitter three-process restart PASS: '+str(output),flush=True)

if __name__=='__main__':
    try:main()
    except (RuntimeError,subprocess.TimeoutExpired) as error:
        print('Transmitter smoke failed: '+str(error),file=sys.stderr)
        sys.exit(1)
