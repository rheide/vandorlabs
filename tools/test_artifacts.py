#!/usr/bin/env python3
"""Retention for explicitly owned, completed test runs; never a general directory cleaner."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import socket
import subprocess
import sys
import threading
import time
import uuid

KEEP_ENV = 'VANDOR_LABS_TEST_KEEP_ARTIFACTS'
EVIDENCE_DIR = 'evidence'
MAX_FAILED_RUNS = 2
MAX_FAILED_BYTES = 512 * 1024 * 1024
MAX_FAILURE_BYTES = 256 * 1024 * 1024
MAX_EVIDENCE_BYTES = 64 * 1024 * 1024
MAX_EVIDENCE_FILES = 256
MAX_LOG_BYTES = 4 * 1024 * 1024
EVIDENCE_GRACE_SECONDS = 24 * 60 * 60
MAX_COMPACT_RUNS = 20
MARKER = '.vandor-test-run.json'
FINISHED = '.vandor-test-finished.json'
LEASES = '.vandor-test-leases'
LOCK = '.vandor-test-finalizing'
OWNER = 'vandorlabs-test-artifacts-v1'


def _keep():
    return os.environ.get(KEEP_ENV, '').lower() in ('1', 'true', 'yes')


def _write(path, value):
    temporary = path.with_name(path.name + '.' + uuid.uuid4().hex + '.tmp')
    try:
        temporary.write_text(json.dumps(value, indent=2, sort_keys=True) + '\n', encoding='utf-8')
        os.replace(str(temporary), str(path))
    finally:
        if temporary.exists():
            temporary.unlink()


def _read(path):
    if path.is_symlink() or not path.is_file():
        raise ValueError('Expected owned regular metadata file: ' + str(path))
    return json.loads(path.read_text(encoding='utf-8'))


def _relative(value):
    path = Path(value)
    if path.is_absolute() or not path.parts or any(p in ('.', '..') for p in path.parts) or str(path) == '.':
        raise ValueError('Disposable paths must be named children of the run')
    # Also reject Windows absolute/drive/UNC forms when validating on Linux.
    if ('\\' in str(value) and os.name != 'nt') or ':' in str(value):
        raise ValueError('Use portable relative disposable paths')
    return path.as_posix()


def _disposable(value):
    name = _relative(value)
    if Path(name).parts[0] in (MARKER, FINISHED, LEASES, LOCK, EVIDENCE_DIR, 'retention.json'):
        raise ValueError('Retention control paths cannot be disposable')
    return name


def _owned_source(metadata, value):
    path = Path(_relative(value))
    if not any(path == Path(name) or Path(name) in path.parents for name in metadata['disposable_paths']):
        raise ValueError('Evidence source is outside registered disposable paths')
    return path.as_posix()


def _root(path):
    path = Path(path).absolute()
    if path.is_symlink():
        raise ValueError('A run directory cannot be a symlink')
    return path.resolve(strict=True)


def _metadata(root):
    data = _read(root / MARKER)
    if not isinstance(data, dict):
        raise ValueError('Invalid ownership metadata')
    if data.get('owner') != OWNER or data.get('root') != str(root) or data.get('version') != 1:
        raise ValueError('Not an owned test run at its original location')
    if not isinstance(data.get('id'), str):
        raise ValueError('Missing run identity')
    uuid.UUID(data['id'])
    lease_dir = _target(root, LEASES)
    if not lease_dir.is_dir():
        raise ValueError('Missing owned lease directory')
    if not isinstance(data.get('disposable_paths'), list):
        raise ValueError('Invalid disposable path registry')
    for value in data['disposable_paths']:
        _disposable(value)
    return data


def _target(root, value):
    target = root
    for component in Path(_relative(value)).parts:
        target = target / component
        if target.is_symlink():
            raise ValueError('Refusing a disposable path through a symlink: ' + str(target))
    return target


def _size(path):
    if not path.exists():
        return 0
    if path.is_file():
        return path.stat().st_size
    total = 0
    for directory, dirs, files in os.walk(str(path), followlinks=False):
        dirs[:] = [d for d in dirs if not (Path(directory) / d).is_symlink()]
        for name in files:
            file = Path(directory) / name
            if not file.is_symlink():
                total += file.stat().st_size
    return total


def _processes():
    """PID -> (parent, group, birth, state), or None if safety cannot be established."""
    if os.name == 'nt':
        # Toolhelp includes descendants after their leader exits; no shell/tasklist parsing.
        import ctypes
        from ctypes import wintypes
        class Entry(ctypes.Structure):
            _fields_ = [('size', wintypes.DWORD), ('usage', wintypes.DWORD), ('pid', wintypes.DWORD),
                        ('heap', ctypes.c_size_t), ('module', wintypes.DWORD), ('threads', wintypes.DWORD),
                        ('parent', wintypes.DWORD), ('priority', wintypes.LONG), ('flags', wintypes.DWORD),
                        ('exe', wintypes.WCHAR * 260)]
        kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        kernel.CreateToolhelp32Snapshot.argtypes = [wintypes.DWORD, wintypes.DWORD]
        kernel.CreateToolhelp32Snapshot.restype = wintypes.HANDLE
        kernel.Process32FirstW.argtypes = [wintypes.HANDLE, ctypes.POINTER(Entry)]
        kernel.Process32FirstW.restype = wintypes.BOOL
        kernel.Process32NextW.argtypes = [wintypes.HANDLE, ctypes.POINTER(Entry)]
        kernel.Process32NextW.restype = wintypes.BOOL
        kernel.CloseHandle.argtypes = [wintypes.HANDLE]
        kernel.CloseHandle.restype = wintypes.BOOL
        handle = kernel.CreateToolhelp32Snapshot(2, 0)
        if handle == ctypes.c_void_p(-1).value:
            return None
        result, entry = {}, Entry()
        entry.size = ctypes.sizeof(entry)
        try:
            ok = kernel.Process32FirstW(handle, ctypes.byref(entry))
            if not ok:
                return None
            while ok:
                result[int(entry.pid)] = (int(entry.parent), None, None, '?')
                ok = kernel.Process32NextW(handle, ctypes.byref(entry))
            return result if ctypes.get_last_error() == 18 else None
        finally:
            kernel.CloseHandle(handle)
    if not Path('/proc').is_dir():
        return None
    result = {}
    for path in Path('/proc').iterdir():
        if not path.name.isdigit():
            continue
        try:
            text = (path / 'stat').read_text()
            fields = text[text.rfind(')') + 2:].split()
            result[int(path.name)] = (int(fields[1]), int(fields[2]), fields[19], fields[0])
        except (FileNotFoundError, ProcessLookupError):
            pass
        except PermissionError:
            # An incomplete process snapshot must not authorize deletion.
            return None
    return result


def _alive(record, table):
    current = table.get(record['pid'])
    return current is not None and current[3] != 'Z' and (
        record.get('birth') is None or record['birth'] == current[2])


def _quiet(lease):
    processes = lease.get('processes', [])
    if not processes:
        return True
    table = _processes()
    if table is None or lease.get('host') != socket.gethostname():
        return False
    for process in processes:
        if _alive(process, table):
            return False
        group = process.get('group')
        if group is not None and any(p[1] == group and p[3] != 'Z' for p in table.values()):
            return False
    return True


class ArtifactRun:
    """Create immediately after mkdtemp. Finish only after the complete multi-phase suite."""
    def __init__(self, run_dir, disposable_paths=(), keep=None, owner_pid=None):
        self.run_dir = _root(run_dir)
        if any(self.run_dir.iterdir()):
            raise ValueError('Only a fresh empty test directory may be claimed')
        paths = list(dict.fromkeys(_disposable(p) for p in disposable_paths))
        for first in paths:
            for second in paths:
                if first != second and Path(first) in Path(second).parents:
                    raise ValueError('Overlapping disposable paths')
        self.metadata = dict(owner=OWNER, version=1, id=str(uuid.uuid4()), root=str(self.run_dir),
                             disposable_paths=paths, keep=_keep() if keep is None else bool(keep), created=time.time())
        _write(self.run_dir / MARKER, self.metadata)
        (self.run_dir / LEASES).mkdir()
        self.token = uuid.uuid4().hex
        self.lease = dict(id=self.metadata['id'], host=socket.gethostname(), pid=owner_pid or os.getpid(),
                          closed=False, processes=[])
        self._setup()
        self._save()

    def _setup(self):
        self._mutex = threading.RLock()
        self._stop = threading.Event()
        self._thread = None

    @classmethod
    def resume(cls, run_dir, token):
        obj = cls.__new__(cls)
        obj.run_dir = _root(run_dir)
        obj.metadata = _metadata(obj.run_dir)
        if len(token) != 32 or any(c not in '0123456789abcdef' for c in token):
            raise ValueError('Invalid lease token')
        obj.token = token
        obj.lease = _read(obj.run_dir / LEASES / (token + '.json'))
        if obj.lease.get('id') != obj.metadata['id']:
            raise ValueError('Lease does not own this run')
        obj._setup()
        return obj

    @classmethod
    def attach(cls, run_dir):
        """Acquire an independent phase lease; all leases must explicitly finish."""
        root = _root(run_dir)
        metadata = _metadata(root)
        lock = root / LOCK
        lock.mkdir()
        try:
            if (root / FINISHED).exists():
                raise ValueError('A completed run cannot start another phase')
            obj = cls.__new__(cls)
            obj.run_dir, obj.metadata = root, metadata
            obj.token = uuid.uuid4().hex
            obj.lease = dict(id=metadata['id'], host=socket.gethostname(), pid=os.getpid(),
                             closed=False, processes=[])
            obj._setup()
            obj._save()
            return obj
        finally:
            lock.rmdir()

    def _validate(self):
        root = _root(self.run_dir)
        current = _metadata(root)
        if current['id'] != self.metadata['id']:
            raise ValueError('Run ownership changed')
        return root

    def _save(self):
        self._validate()
        _write(_target(self.run_dir, LEASES + '/' + self.token + '.json'), self.lease)

    def _refresh(self):
        with self._mutex:
            table = _processes()
            if table is None:
                return
            records = self.lease['processes']
            known = {r['pid'] for r in records if _alive(r, table)}
            groups = {r['group'] for r in records if r.get('group') is not None}
            existing = {(r['pid'], r.get('birth')) for r in records}
            changed = True
            while changed:
                changed = False
                for pid, info in table.items():
                    if info[3] == 'Z' or (pid, info[2]) in existing:
                        continue
                    if info[0] in known or info[1] in groups:
                        record = dict(pid=pid, birth=info[2], group=info[1] if info[1] in groups else None)
                        records.append(record)
                        existing.add((pid, info[2]))
                        known.add(pid)
                        changed = True
            self._save()

    def track_process(self, process):
        """Record the leader, isolated POSIX group and observed descendants during launch."""
        if self.lease['closed']:
            raise ValueError('A finished lease cannot launch another phase')
        table = _processes()
        info = None if table is None else table.get(process.pid)
        group = None
        if os.name != 'nt':
            group = info[1] if info is not None and info[1] != os.getpgrp() else process.pid
        record = dict(pid=process.pid, birth=info[2] if info else None, group=group)
        with self._mutex:
            self.lease['processes'].append(record)
            self._save()
        self._refresh()
        if self._thread is None:
            def watch():
                while not self._stop.wait(.25):
                    try:
                        self._refresh()
                    except OSError:
                        # Failed tracking forbids cleanup rather than losing process ownership.
                        with self._mutex:
                            self.lease['tracking_failed'] = True
                            self._save()
                        return
            self._thread = threading.Thread(target=watch, daemon=True)
            self._thread.start()
        return process

    def stop_tracking(self):
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=2)
        self._refresh()

    def finish(self, success, active_processes=()):
        """Presence of caller-confirmed live handles/groups blocks deletion unconditionally."""
        self._validate()
        self.stop_tracking()
        if active_processes or self.lease.get('tracking_failed') or not _quiet(self.lease):
            return {'status': 'active', 'removed_bytes': 0}
        self.lease.update(closed=True, success=bool(success))
        self._save()
        root = self.run_dir
        lock = root / LOCK
        try:
            lock.mkdir()
        except FileExistsError:
            return {'status': 'busy', 'removed_bytes': 0}
        try:
            leases = [_read(path) for path in (root / LEASES).glob('*.json')]
            if not leases or any(l.get('id') != self.metadata['id'] or not l.get('closed') or not _quiet(l) for l in leases):
                return {'status': 'active', 'removed_bytes': 0}
            keep = self.metadata['keep'] or _keep()
            completed = dict(success=all(l.get('success') for l in leases), keep=keep, finished=time.time())
            if not (root / FINISHED).exists():
                if not keep:
                    completed['evidence'], completed['preserve'] = _evidence(root, self.metadata)
                _write(root / FINISHED, completed)
        finally:
            lock.rmdir()
        if keep:
            _write(root / 'retention.json', dict(status='kept', removed_bytes=0, reason=KEEP_ENV))
        reports = sweep(root.parent)
        return next((r for r in reports if r['run_dir'] == str(root)), {'status': 'kept', 'removed_bytes': 0})

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        self.finish(exc_type is None)
        return False


def _evidence(root, metadata):
    destination = _target(root, EVIDENCE_DIR)
    destination.mkdir(exist_ok=True)
    candidates = []
    skip = {'mods', 'assets', 'libraries', 'resourcepacks', 'shaderpacks', 'region', 'poi', 'entities', '.cache', 'cache'}
    for name in metadata['disposable_paths']:
        target = _target(root, name)
        if not target.is_dir():
            continue
        for directory, dirs, files in os.walk(str(target), followlinks=False):
            dirs[:] = [d for d in dirs if d not in skip and not (Path(directory) / d).is_symlink()]
            for filename in files:
                path = Path(directory) / filename
                parts = set(path.relative_to(target).parts[:-1])
                if path.is_symlink():
                    continue
                if (path.suffix.lower() in ('.log', '.txt') and ('logs' in parts or 'crash-reports' in parts)) or (
                    path.suffix.lower() in ('.png', '.jpg', '.jpeg') and 'screenshots' in parts) or filename in (
                    'level.dat', 'level.dat_old', 'server.properties', 'options.txt') or (
                    path.suffix.lower() in ('.json', '.txt') and any(x in filename.lower() for x in ('report', 'manifest', 'assert', 'artifact'))):
                    candidates.append(path)
    candidates.sort(key=lambda p: (0 if p.suffix == '.log' or 'crash-reports' in p.parts else 1, -p.stat().st_mtime))
    result, preserve, used = [], [], 0
    for source in candidates:
        if source.name not in ('level.dat', 'level.dat_old', 'server.properties', 'options.txt'):
            preserve.append(dict(path=source.relative_to(root).as_posix(), sha256=_hash(source)))
    for source in candidates:
        if len(result) >= MAX_EVIDENCE_FILES:
            break
        size = source.stat().st_size
        tail = size > MAX_LOG_BYTES and source.suffix.lower() in ('.log', '.txt')
        count = min(size, MAX_LOG_BYTES) if tail else size
        if count > 16 * 1024 * 1024 or used + count > MAX_EVIDENCE_BYTES:
            continue
        relative = source.relative_to(root)
        target = _target(root, (Path(EVIDENCE_DIR) / relative).as_posix())
        target.parent.mkdir(parents=True, exist_ok=True)
        with source.open('rb') as reader:
            if tail:
                reader.seek(-count, os.SEEK_END)
            content = reader.read(count)
        target.write_bytes(content)
        used += len(content)
        result.append(dict(path=target.relative_to(root).as_posix(), source=relative.as_posix(),
                           bytes=len(content), original_bytes=size, tail=tail, sha256=hashlib.sha256(content).hexdigest()))
    return result, preserve


def _hash(path):
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def _prune(target, preserve):
    if not target.exists() and not target.is_symlink():
        return
    if target.is_symlink():
        target.unlink()
    elif target.is_file():
        if target not in preserve:
            target.unlink()
    elif not any(target == p or target in p.parents for p in preserve):
        shutil.rmtree(target)
    else:
        for child in target.iterdir():
            _prune(child, preserve)
        if not any(target.iterdir()):
            target.rmdir()


def _bound_evidence(root, outcome, retain):
    if outcome.get('compact_evicted') or (retain and outcome.get('compact_bounded')):
        return
    metadata = _metadata(root)
    archived = {item['source']: item for item in outcome.get('evidence', [])}
    for item in outcome.get('preserve', []):
        path = _target(root, _owned_source(metadata, item['path']))
        if not path.is_file() or _hash(path) != item['sha256']:
            continue  # Keep later user/consumer edits.
        saved = archived.get(item['path']) if retain else None
        if saved is None:
            path.unlink()
        elif saved['tail']:
            source = _target(root, saved['path'])
            shutil.copyfile(source, path)
            item['sha256'] = saved['sha256']
    if not retain:
        for item in outcome.get('evidence', []):
            path = _target(root, item['path'])
            if path.is_file() and _hash(path) == item['sha256']:
                path.unlink()
    outcome['compact_bounded'] = True
    outcome['compact_evicted'] = not retain
    _write(root / FINISHED, outcome)


def sweep(parent):
    """Prune only marked, explicitly finished runs. Never infer completion from a dead PID."""
    finished = []
    for root in Path(parent).iterdir():
        if root.is_symlink() or not root.is_dir() or not (root / FINISHED).is_file():
            continue
        try:
            root = _root(root)
            metadata, outcome = _metadata(root), _read(root / FINISHED)
            if not isinstance(outcome, dict) or not isinstance(outcome.get('finished'), (int, float)) or not isinstance(outcome.get('success'), bool):
                raise ValueError('Invalid completion metadata')
            if metadata.get('keep') or outcome.get('keep') or _keep():
                continue
            targets = [_target(root, p) for p in metadata['disposable_paths']]
            finished.append((root, metadata, outcome, sum(_size(p) for p in targets)))
        except ValueError:
            continue
    failed_count, failed_bytes, compact_count, reports = 0, 0, 0, []
    for root, metadata, outcome, size in sorted(finished, key=lambda r: r[2]['finished'], reverse=True):
        retain = not outcome.get('payload_pruned') and not outcome['success'] and size <= MAX_FAILURE_BYTES and failed_count < MAX_FAILED_RUNS and failed_bytes + size <= MAX_FAILED_BYTES
        if retain and size:
            failed_count += 1
            failed_bytes += size
        removed = 0
        lock = root / LOCK
        try:
            lock.mkdir()
        except FileExistsError:
            continue
        try:
            # An unfinished or corrupt lease always takes priority over disk reclamation.
            leases = [_read(p) for p in (root / LEASES).glob('*.json')]
            if not leases or any(not l.get('closed') or l.get('id') != metadata['id'] for l in leases):
                continue
            if not retain and not outcome.get('payload_pruned'):
                preserve = {_target(root, _owned_source(metadata, item['path'])) for item in outcome.get('preserve', [])}
                for relative in metadata['disposable_paths']:
                    target = _target(root, relative)
                    before = _size(target)
                    _prune(target, preserve)
                    removed += before - _size(target)
                outcome['payload_pruned'] = True
                _write(root / FINISHED, outcome)
            if outcome['finished'] < time.time() - EVIDENCE_GRACE_SECONDS:
                _bound_evidence(root, outcome, compact_count < MAX_COMPACT_RUNS)
                compact_count += 1
            summary = dict(status='retained-failure' if retain else 'pruned', removed_bytes=removed,
                           payload_bytes=size if retain else 0, evidence_files=len(outcome.get('evidence', [])))
            _write(root / 'retention.json', summary)
            reports.append(dict(summary, run_dir=str(root)))
        finally:
            lock.rmdir()
    return reports


def _main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='action', required=True)
    begin = commands.add_parser('begin')
    begin.add_argument('--run-dir', type=Path, required=True)
    begin.add_argument('--pid', type=int)
    begin.add_argument('--disposable', action='append', default=[])
    finish = commands.add_parser('finish')
    finish.add_argument('--run-dir', type=Path, required=True)
    finish.add_argument('--lease', required=True)
    finish.add_argument('--exit-code', type=int, required=True)
    run = commands.add_parser('run')
    run.add_argument('--run-dir', type=Path, required=True)
    run.add_argument('--lease', required=True)
    run.add_argument('command', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    if args.action == 'begin':
        print(ArtifactRun(args.run_dir, args.disposable, owner_pid=args.pid).token)
    elif args.action == 'finish':
        print(json.dumps(ArtifactRun.resume(args.run_dir, args.lease).finish(args.exit_code == 0), sort_keys=True))
    else:
        arguments = args.command[1:] if args.command[:1] == ['--'] else args.command
        if not arguments:
            parser.error('run requires a command after --')
        owner = ArtifactRun.resume(args.run_dir, args.lease)
        options = {'creationflags': subprocess.CREATE_NEW_PROCESS_GROUP} if os.name == 'nt' else {'start_new_session': True}
        process = owner.track_process(subprocess.Popen(arguments, **options))
        def interrupted(signum, frame):
            raise KeyboardInterrupt()
        previous = signal.signal(signal.SIGTERM, interrupted)
        try:
            result = process.wait()
            return result if result >= 0 else 128 - result
        except BaseException:
            if process.poll() is None:
                if os.name == 'nt':
                    subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], check=False, capture_output=True)
                else:
                    os.killpg(process.pid, signal.SIGTERM)
                process.wait(timeout=15)
            raise
        finally:
            signal.signal(signal.SIGTERM, previous)
            owner.stop_tracking()
    return 0


if __name__ == '__main__':
    sys.exit(_main())
