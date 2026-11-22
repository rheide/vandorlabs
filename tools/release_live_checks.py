#!/usr/bin/env python3
"""Run the exact release live-focus inventory with at most two Minecraft lanes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import tempfile
import threading
import time

from test_artifacts import ArtifactRun, _alive, _processes

ROOT = Path(__file__).resolve().parents[1]
LEGACY_FOCI = (
    'render-regressions', 'glass-appearance', 'utility22_card_mounts', 'fluids',
    'bridge-machines', 'utility26', 'arm-live', 'segmented-door',
    'coupler-articulation', 'ramp-axis', 'screen-chips', 'glass-migration',
    'vehicle-render-cost', 'render-density', 'segmented-door-legacy',
    'segmented-door-tall', 'furniture', 'vehicle-design', 'arm-endpoints',
    'arm-joined', 'arm-riders', 'arm-placement', 'arm-wall', 'copy-surfaces',
    'vehicle-ramps', 'vehicle-taxi')
RELEASE_FOCI = LEGACY_FOCI + (
    'navigation-vehicle', 'navigation-world', 'navigation-module', 'module-rack',
    'appliances', 'synthesis-tower', 'maxwell-drive', 'vehicle-status', 'maxwell-cruise', 'vehicle-resources', 'infuser-gui', 'ramp-dialog', 'core-icons',
    'observer-performance', 'navigation-performance')
OPTIONAL_FOCI = {'mekanism-tanks': 'VANDOR_LABS_TEST_MEKANISM',
                 'machine-jei': 'VANDOR_LABS_TEST_JEI'}
PRIORITY = (
    'arm-live', 'arm-endpoints', 'arm-joined', 'arm-riders', 'arm-placement',
    'arm-wall', 'copy-surfaces', 'vehicle-ramps', 'vehicle-taxi',
    'navigation-vehicle', 'navigation-world', 'navigation-module',
    'coupler-articulation', 'ramp-axis', 'screen-chips', 'glass-migration',
    'vehicle-design', 'bridge-machines', 'utility26', 'furniture',
    'module-rack', 'appliances', 'synthesis-tower', 'maxwell-drive', 'vehicle-status', 'maxwell-cruise', 'vehicle-resources', 'infuser-gui', 'ramp-dialog',
    'segmented-door', 'segmented-door-legacy', 'segmented-door-tall',
    'fluids', 'utility22_card_mounts', 'render-regressions', 'glass-appearance',
    'core-icons', 'vehicle-render-cost', 'render-density',
    'observer-performance', 'navigation-performance')
EXCLUSIVE = frozenset(('vehicle-render-cost', 'render-density',
                       'observer-performance', 'navigation-performance'))
assert len(PRIORITY) == len(set(PRIORITY)) and set(PRIORITY) == set(RELEASE_FOCI)


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def command_for(focus, jar, heap, windows=None):
    windows = os.name == 'nt' if windows is None else windows
    if focus in ('navigation-vehicle', 'maxwell-cruise'):
        # Both platforms keep write and cold-reload phases in one owned lane.
        return [sys.executable, 'testclient/test_navigation_vehicle.py',
                '--jar', str(jar), '--max-heap', heap] + (['--focus', focus] if focus == 'maxwell-cruise' else [])
    if windows:
        selector = '--shot-prefix' if focus == 'utility22_card_mounts' else '--focus'
        return [sys.executable, 'testclient/run_windows.py', '--jar', str(jar),
                '--max-heap', heap, selector, focus, '--timeout', '900']
    if focus == 'mekanism-tanks':
        # The existing wrapper owns both save and fresh-JVM verification phases.
        return ['bash', 'testclient/test_mekanism_tank.sh']
    return ['bash', 'testclient/test_viewscreen.sh', '--focus', focus]


class WindowsJob:
    """Own only this launcher tree; kill-on-close also covers detached JVMs."""
    def __init__(self, process):
        self.handle = None
        if os.name != 'nt':
            return
        import ctypes
        from ctypes import wintypes as w
        class Limits(ctypes.Structure):
            _fields_ = [('process_time', ctypes.c_int64), ('job_time', ctypes.c_int64),
                        ('flags', w.DWORD), ('min_working', ctypes.c_size_t),
                        ('max_working', ctypes.c_size_t), ('active', w.DWORD),
                        ('affinity', ctypes.c_size_t), ('priority', w.DWORD), ('scheduling', w.DWORD)]
        class Extended(ctypes.Structure):
            _fields_ = [('limits', Limits), ('io', ctypes.c_uint64 * 6),
                        ('process_memory', ctypes.c_size_t), ('job_memory', ctypes.c_size_t),
                        ('peak_process', ctypes.c_size_t), ('peak_job', ctypes.c_size_t)]
        kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        kernel.CreateJobObjectW.argtypes = [ctypes.c_void_p, w.LPCWSTR]
        kernel.CreateJobObjectW.restype = w.HANDLE
        kernel.SetInformationJobObject.argtypes = [w.HANDLE, ctypes.c_int, ctypes.c_void_p, w.DWORD]
        kernel.AssignProcessToJobObject.argtypes = [w.HANDLE, w.HANDLE]
        kernel.CloseHandle.argtypes = [w.HANDLE]
        handle = kernel.CreateJobObjectW(None, None)
        info = Extended(); info.limits.flags = 0x2000  # JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
        if not handle or not kernel.SetInformationJobObject(handle, 9, ctypes.byref(info), ctypes.sizeof(info)) or not kernel.AssignProcessToJobObject(handle, int(process._handle)):
            if handle:
                kernel.CloseHandle(handle)
            process.terminate(); process.wait()
            raise OSError('Cannot establish owned Windows launcher job')
        self.handle, self.kernel = handle, kernel

    def close(self):
        if self.handle is not None:
            self.kernel.CloseHandle(self.handle)
            self.handle = None


def stop_owned(active, grace=8):
    """Signal only recorded descendants, including nested artifact-run sessions."""
    for item in active.values():
        process = item['process']
        if os.name == 'nt':
            if process.poll() is None:
                try:
                    process.send_signal(signal.CTRL_BREAK_EVENT)
                except OSError:
                    pass
        else:
            item['owner']._refresh()
            table = _processes()
            if table is not None:
                for record in list(item['owner'].lease['processes']):
                    if _alive(record, table):
                        try:
                            os.kill(record['pid'], signal.SIGTERM)
                        except ProcessLookupError:
                            pass
    deadline = time.monotonic() + grace
    while time.monotonic() < deadline:
        if all(item['process'].poll() is not None for item in active.values()):
            break
        time.sleep(.05)
    for item in active.values():
        if os.name != 'nt':
            item['owner']._refresh(); table = _processes()
            if table is not None:
                for record in list(item['owner'].lease['processes']):
                    if _alive(record, table):
                        try:
                            os.kill(record['pid'], signal.SIGKILL)
                        except ProcessLookupError:
                            pass
        item['job'].close()
        if item['process'].poll() is None:
            item['process'].wait(timeout=5)


def artifact_hashes(directory, expected):
    found = []
    for parent, dirs, files in os.walk(str(directory)):
        dirs[:] = [d for d in dirs if d not in ('game', 'natives', 'saves', 'mods', 'assets', 'libraries')]
        if 'artifact.sha256' in files:
            path = Path(parent) / 'artifact.sha256'
            words = path.read_text(encoding='utf-8').split()
            value = words[0] if words else ''
            if not re.fullmatch('[0-9a-fA-F]{64}', value) or value.lower() != expected:
                raise ValueError('Fixture JAR hash mismatch: ' + str(path))
            found.append({'path': str(path), 'sha256': value.lower()})
    return found


def run_release(jar, output, jobs=2, *, names=None, builder=None, stop=None, grace=8,
                report_path=None, scope=None):
    """Internal injections let fake processes exercise the real scheduler without Minecraft."""
    if jobs not in (1, 2):
        raise ValueError('jobs must be 1 or 2')
    jar, output = Path(jar).resolve(), Path(output).resolve()
    expected = sha256(jar)
    scope = scope or ('full' if names is None else 'targeted')
    names = list(PRIORITY if names is None else names)
    optional_skipped = []
    if scope == 'full':
        for focus, variable in OPTIONAL_FOCI.items():
            if os.environ.get(variable):
                names.insert(0 if focus == 'mekanism-tanks' else names.index('core-icons'), focus)
            else:
                optional_skipped.append({'focus': focus, 'reason': variable + ' is not configured'})
    if len(names) != len(set(names)):
        raise ValueError('Duplicate focus in release coverage')
    output.mkdir(parents=True, exist_ok=False)
    stop = threading.Event() if stop is None else stop
    heap = os.environ.get('VANDOR_LABS_TEST_HEAP') or '8G'
    if not re.fullmatch('[1-9][0-9]*[mMgG]', heap):
        raise ValueError('Invalid release heap')
    report = {'schema': 1, 'scope': scope, 'full_release_coverage': False,
              'output': str(output), 'jar': str(jar), 'sha256_start': expected, 'jobs': jobs,
              'expected': names, 'legacy_coverage': list(LEGACY_FOCI),
              'required_coverage': list(RELEASE_FOCI), 'optional_skipped': optional_skipped,
              'results': [],
              'status': 'running', 'infrastructure_error': None}
    active, pending = {}, list(names)

    def publish():
        temporary = output / 'report.json.tmp'
        temporary.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
        os.replace(str(temporary), str(output / 'report.json'))
        if report_path is not None and Path(report_path).resolve() != output / 'report.json':
            target = Path(report_path).resolve()
            target.parent.mkdir(parents=True, exist_ok=True)
            temporary = target.with_name(target.name + '.tmp')
            temporary.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
            os.replace(str(temporary), str(target))

    publish()
    print('Live release output: ' + str(output), flush=True)
    try:
        while pending or active:
            if stop.is_set():
                report['infrastructure_error'] = 'Interrupted; stopped owned focus trees'
                stop_owned(active, grace)
            # Ordinary assertion failures collect the complete inventory. A changed artifact,
            # cancellation or inability to launch invalidates further results instead.
            while pending and len(active) < jobs and not report['infrastructure_error']:
                name = pending[0]
                if active and (name in EXCLUSIVE or any(n in EXCLUSIVE for n in active)):
                    break
                if sha256(jar) != expected:
                    report['infrastructure_error'] = 'Release JAR changed before launch'
                    break
                pending.pop(0)
                directory = output / name
                directory.mkdir()
                owner = ArtifactRun(directory, ['game'])
                (directory / 'artifacts').mkdir()
                env = dict(os.environ, VANDOR_LABS_TEST_SKIP_BUILD='true',
                           VANDOR_LABS_TEST_OPTIFINE='', VANDOR_LABS_TEST_SHADERPACK='',
                           VANDOR_LABS_TEST_HEAP=heap, VANDOR_LABS_TEST_JAR=str(jar),
                           VANDOR_LABS_TEST_RUN_ROOT=str(directory / 'artifacts'),
                           VANDOR_LABS_TEST_GAME_DIR=str(directory / 'game'))
                env.pop('VANDOR_LABS_REPRO_OUT', None)
                env.pop('VANDOR_LABS_MEKANISM_TANK_RESTART', None)
                command = (builder(name, jar, heap) if builder else command_for(name, jar, heap))
                log = (directory / 'focus.log').open('w', encoding='utf-8')
                row = {'focus': name, 'command': command, 'exclusive': name in EXCLUSIVE,
                       'directory': str(directory), 'start': time.time(), 'status': 'running'}
                try:
                    options = {'creationflags': subprocess.CREATE_NEW_PROCESS_GROUP} if os.name == 'nt' else {'start_new_session': True}
                    process = owner.track_process(subprocess.Popen(command, cwd=ROOT, env=env,
                                      stdout=log, stderr=subprocess.STDOUT, **options))
                    job = WindowsJob(process)
                except Exception as error:
                    log.close(); owner.finish(False)
                    row.update(status='failed', error=str(error), end=time.time())
                    report['results'].append(row)
                    report['infrastructure_error'] = 'Focus launch failed: ' + str(error)
                    break
                active[name] = dict(process=process, owner=owner, job=job, log=log, row=row)
                print('START ' + name + ' -> ' + str(directory / 'focus.log'), flush=True)
                publish()
                if name in EXCLUSIVE:
                    break
            finished = []
            for name, item in list(active.items()):
                code = item['process'].poll()
                if code is None:
                    continue
                row = item['row']; row.update(exit_code=code, end=time.time())
                item['log'].close()
                try:
                    if sha256(jar) != expected:
                        raise ValueError('Release JAR changed during live validation')
                    row['artifact_hashes'] = artifact_hashes(Path(row['directory']) / 'artifacts', expected)
                    required_hashes = 2 if name in ('navigation-vehicle', 'maxwell-cruise') or (name == 'mekanism-tanks' and os.name != 'nt') else 1
                    if code == 0 and len(row['artifact_hashes']) < required_hashes:
                        raise ValueError('Missing fixture JAR hashes: ' + name + ' requires ' + str(required_hashes))
                    row['status'] = 'passed' if code == 0 and not stop.is_set() else 'failed'
                except (OSError, ValueError) as error:
                    row.update(status='failed', error=str(error))
                    report['infrastructure_error'] = str(error)
                retention = item['owner'].finish(row['status'] == 'passed')
                row['retention'] = retention
                if retention['status'] in ('active', 'busy'):
                    row.update(status='failed', error='Owned descendants did not exit')
                    report['infrastructure_error'] = row['error']
                    stop_owned({name: item}, grace)
                    item['owner'].finish(False)
                item['job'].close()
                report['results'].append(row); finished.append(name)
                print(row['status'].upper() + ' ' + name + ' exit=' + str(code), flush=True)
            for name in finished:
                del active[name]
            publish()
            if report['infrastructure_error'] and not active:
                break
            if active:
                time.sleep(.1)
    except BaseException:
        stop_owned(active, grace)
        for item in active.values():
            item['log'].close(); item['owner'].finish(False)
        report['infrastructure_error'] = 'Scheduler exception; owned focus trees stopped'
        raise
    finally:
        try:
            report['sha256_end'] = sha256(jar)
        except OSError as error:
            report['sha256_end'] = None; report['infrastructure_error'] = str(error)
        report['not_run'] = pending
        completed = [r['focus'] for r in report['results']]
        report['failures'] = [r['focus'] for r in report['results'] if r['status'] != 'passed']
        complete = len(completed) == len(names) and set(completed) == set(names)
        report['complete_coverage'] = complete
        report['full_release_coverage'] = scope == 'full' and complete and set(RELEASE_FOCI).issubset(completed)
        report['status'] = 'passed' if complete and not report['failures'] and not report['infrastructure_error'] and report['sha256_end'] == expected else 'failed'
        publish()
    print(('Release live inventory ' if scope == 'full' else 'Targeted live checks ') + report['status'].upper() + ': ' + str(output / 'report.json'), flush=True)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--jobs', type=int, choices=(1, 2), default=2)
    parser.add_argument('--report', type=Path, help='Also atomically write the summary at this path')
    parser.add_argument('--focus', action='append', choices=RELEASE_FOCI + tuple(OPTIONAL_FOCI),
                        help='Targeted rerun only; never reported as the full live inventory')
    args = parser.parse_args()
    if args.focus and len(args.focus) != len(set(args.focus)):
        parser.error('Do not repeat a focus')
    for focus, variable in OPTIONAL_FOCI.items():
        if args.focus and focus in args.focus and not os.environ.get(variable):
            parser.error(focus + ' requires ' + variable)
    base = Path(os.environ.get('VANDOR_LABS_RELEASE_RUN_ROOT') or os.environ.get('VANDOR_LABS_TEST_RUN_ROOT') or ROOT / 'build/release-live').resolve()
    base.mkdir(parents=True, exist_ok=True)
    # run_release claims a fresh named child; never a caller's existing directory.
    output = Path(tempfile.mkdtemp(prefix='live-', dir=str(base))); output.rmdir()
    stop = threading.Event()
    previous = {}
    for sig in (signal.SIGINT, signal.SIGTERM):
        previous[sig] = signal.signal(sig, lambda number, frame: stop.set())
    try:
        return 0 if run_release(args.jar, output, args.jobs, stop=stop,
                               names=args.focus, report_path=args.report)['status'] == 'passed' else 1
    finally:
        for sig, handler in previous.items():
            signal.signal(sig, handler)


if __name__ == '__main__':
    sys.exit(main())
