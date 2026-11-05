#!/usr/bin/env python3
"""Temporary-directory/process contracts; never starts Minecraft or touches real test output."""
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
import test_artifacts as retention


class RetentionChecks(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='artifact retention ')
        self.parent = Path(self.temporary.name)
        self.environment = patch.dict(os.environ, {retention.KEEP_ENV: ''})
        self.environment.start()

    def tearDown(self):
        self.environment.stop()
        self.temporary.cleanup()

    def run_dir(self, name='run', paths=('game',)):
        root = self.parent / name
        root.mkdir()
        return root, retention.ArtifactRun(root, disposable_paths=paths)

    @staticmethod
    def payload(root, size=20):
        game = root / 'game'
        game.mkdir()
        (game / 'world.bin').write_bytes(b'x' * size)
        return game

    def test_success_extracts_only_bounded_diagnostics_and_keeps_reports(self):
        root, owner = self.run_dir()
        game = self.payload(root)
        (game / 'logs').mkdir()
        (game / 'logs/latest.log').write_text('before\nPASS after\n')
        (game / 'screenshots').mkdir()
        (game / 'screenshots/view.png').write_bytes(b'actual screen bytes')
        (root / 'reference.json').write_text('{"oracle":1}')
        (root / 'artifact.sha256').write_text('known jar hash')
        result = owner.finish(True)
        self.assertEqual(result['status'], 'pruned')
        self.assertFalse((game / 'world.bin').exists())
        self.assertTrue((game / 'logs/latest.log').exists())
        self.assertTrue((game / 'screenshots/view.png').exists())
        self.assertIn('PASS after', (root / 'evidence/game/logs/latest.log').read_text())
        self.assertEqual((root / 'evidence/game/screenshots/view.png').read_bytes(), b'actual screen bytes')
        self.assertTrue((root / 'reference.json').is_file())
        self.assertTrue((root / 'artifact.sha256').is_file())
        proof = json.loads((root / retention.FINISHED).read_text())['evidence']
        self.assertTrue(all(len(x['sha256']) == 64 for x in proof))

    def test_failures_bounded_by_count_single_and_total_bytes(self):
        with patch.multiple(retention, MAX_FAILED_RUNS=2, MAX_FAILED_BYTES=150, MAX_FAILURE_BYTES=100):
            roots = []
            for n in range(3):
                root, owner = self.run_dir('failure%d' % n)
                self.payload(root, 60)
                owner.finish(False)
                roots.append(root)
                time.sleep(.002)
            self.assertFalse((roots[0] / 'game').exists())
            self.assertTrue(all((r / 'game').exists() for r in roots[1:]))
            root, owner = self.run_dir('oversized')
            self.payload(root, 101)
            owner.finish(False)
            self.assertFalse((root / 'game').exists())
            self.assertTrue(all((r / 'game').exists() for r in roots[1:]))

    def test_keep_opt_out_persists_after_environment_changes(self):
        with patch.dict(os.environ, {retention.KEEP_ENV: '1'}):
            root, owner = self.run_dir()
            self.payload(root)
            owner.finish(True)
        retention.sweep(self.parent)
        self.assertTrue((root / 'game/world.bin').exists())

    def test_unmarked_unfinished_and_explicit_active_handles_are_preserved(self):
        unknown = self.parent / 'unmarked'
        unknown.mkdir()
        (unknown / 'game').mkdir()
        root, owner = self.run_dir()
        self.payload(root)
        retention.sweep(self.parent)
        self.assertTrue((root / 'game').exists())
        self.assertEqual(owner.finish(True, active_processes=[object()])['status'], 'active')
        self.assertFalse((root / retention.FINISHED).exists())
        self.assertTrue((unknown / 'game').exists())
        self.assertTrue((root / 'game').exists())

    def test_all_phase_leases_must_finish_before_shared_world_deletion(self):
        root, first = self.run_dir()
        game = self.payload(root)
        second = retention.ArtifactRun.attach(root)
        self.assertEqual(first.finish(True)['status'], 'active')
        self.assertTrue(game.is_dir())
        self.assertFalse((root / retention.FINISHED).exists())
        (game / 'read-phase-proof.txt').write_text('same saved owner')
        second.finish(True)
        self.assertFalse(game.exists())
        with self.assertRaises(ValueError):
            retention.ArtifactRun.attach(root)

    def test_concurrent_phase_finish_does_not_delete_an_active_lease(self):
        root, first = self.run_dir()
        self.payload(root)
        second = retention.ArtifactRun.attach(root)
        errors = []
        def finish(owner):
            try:
                owner.finish(True)
            except Exception as exc:
                errors.append(exc)
        threads = [threading.Thread(target=finish, args=(x,)) for x in (first, second)]
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join()
        self.assertEqual(errors, [])
        retention.sweep(self.parent)
        self.assertFalse((root / 'game').exists())

    def test_symlink_escape_is_never_followed(self):
        outside = self.parent / 'custom game'
        outside.mkdir()
        sentinel = outside / 'user-world.dat'
        sentinel.write_text('user-owned')
        root, owner = self.run_dir()
        try:
            (root / 'game').symlink_to(outside, target_is_directory=True)
        except (OSError, NotImplementedError):
            self.skipTest('symlinks unavailable')
        with self.assertRaises(ValueError):
            owner.finish(True)
        self.assertEqual(sentinel.read_text(), 'user-owned')
        self.assertFalse((root / retention.FINISHED).exists())
        (root / 'game').unlink()
        game = self.payload(root)
        (game / 'external').symlink_to(outside, target_is_directory=True)
        owner.finish(True)
        self.assertFalse(game.exists())
        self.assertTrue(sentinel.exists())

    def test_unsafe_registration_and_existing_user_directory_are_rejected(self):
        for name in ('../user', '/user', '.', 'a/../user', 'C:\\world', '\\\\host\\share', retention.MARKER, retention.LEASES):
            root = self.parent / ('case' + str(len(list(self.parent.iterdir()))))
            root.mkdir()
            with self.assertRaises(ValueError):
                retention.ArtifactRun(root, disposable_paths=[name])
            self.assertEqual(list(root.iterdir()), [])
        root = self.parent / 'not-fresh'
        root.mkdir()
        (root / 'user.data').write_text('keep')
        with self.assertRaises(ValueError):
            retention.ArtifactRun(root, disposable_paths=['user.data'])
        self.assertTrue((root / 'user.data').exists())

    def test_log_tails_are_bounded_and_labelled(self):
        root, owner = self.run_dir()
        game = self.payload(root)
        (game / 'logs').mkdir()
        (game / 'logs/latest.log').write_bytes(b'A' * 100 + b'finalPASS')
        with patch.multiple(retention, MAX_LOG_BYTES=16, MAX_EVIDENCE_BYTES=20, EVIDENCE_GRACE_SECONDS=0):
            owner.finish(True)
        result = (root / 'evidence/game/logs/latest.log').read_bytes()
        self.assertEqual(len(result), 16)
        self.assertTrue(result.endswith(b'finalPASS'))
        entry = json.loads((root / retention.FINISHED).read_text())['evidence'][0]
        self.assertTrue(entry['tail'])
        self.assertEqual(entry['original_bytes'], 109)

    def test_current_original_paths_are_not_truncated_and_world_header_is_archived_only(self):
        root, owner = self.run_dir()
        game = self.payload(root)
        (game / 'logs').mkdir()
        original = b'beforePASS' + b'x' * 100 + b'afterPASS'
        (game / 'logs/latest.log').write_bytes(original)
        (game / 'world').mkdir()
        (game / 'world/level.dat').write_bytes(b'saved world header')
        with patch.multiple(retention, MAX_LOG_BYTES=16, MAX_EVIDENCE_BYTES=128):
            owner.finish(True)
            self.assertEqual((game / 'logs/latest.log').read_bytes(), original)
            self.assertFalse((game / 'world/level.dat').exists())
            self.assertTrue((root / 'evidence/game/world/level.dat').exists())
            with patch.object(retention.time, 'time', return_value=time.time() + 2 * 86400):
                retention.sweep(self.parent)
            self.assertEqual((game / 'logs/latest.log').read_bytes(), original[-16:])

    def test_lease_directory_symlink_is_rejected_before_any_outside_write(self):
        root, owner = self.run_dir()
        self.payload(root)
        outside = self.parent / 'outside-leases'
        (root / retention.LEASES).rename(outside)
        before = (outside / (owner.token + '.json')).read_bytes()
        try:
            (root / retention.LEASES).symlink_to(outside, target_is_directory=True)
        except (OSError, NotImplementedError):
            self.skipTest('symlinks unavailable')
        with self.assertRaises(ValueError):
            owner.finish(True)
        self.assertEqual((outside / (owner.token + '.json')).read_bytes(), before)
        self.assertTrue((root / 'game/world.bin').exists())

    def test_windows_process_snapshot_uses_pointer_width_handles_and_fails_closed(self):
        import ctypes
        from ctypes import wintypes
        class Function:
            def __init__(self, callback):
                self.callback = callback
            def __call__(self, *args):
                return self.callback(*args)
        class Kernel:
            def __init__(self):
                self.closed = []
                self.CreateToolhelp32Snapshot = Function(lambda flags, pid: 0x1234567800000001)
                def first(handle, pointer):
                    pointer._obj.pid, pointer._obj.parent = 111, 1
                    return True
                self.Process32FirstW = Function(first)
                self.Process32NextW = Function(lambda handle, pointer: False)
                self.CloseHandle = Function(lambda handle: self.closed.append(handle))
        kernel = Kernel()
        with patch.object(retention.os, 'name', 'nt'), patch.object(ctypes, 'WinDLL', return_value=kernel, create=True), patch.object(ctypes, 'get_last_error', return_value=18, create=True):
            self.assertEqual(retention._processes(), {111: (1, None, None, '?')})
            self.assertEqual(kernel.closed, [0x1234567800000001])
            self.assertEqual(kernel.Process32FirstW.argtypes[0], wintypes.HANDLE)
            self.assertEqual(kernel.CloseHandle.argtypes, [wintypes.HANDLE])
            kernel.Process32FirstW.callback = lambda handle, pointer: False
            self.assertIsNone(retention._processes())

    def test_cli_preserves_world_across_phases_then_removes_it(self):
        root = self.parent / 'shell run with spaces'
        root.mkdir()
        tool = str(Path(retention.__file__))
        def command(*args):
            return subprocess.run([sys.executable, tool, *map(str, args)], check=True, capture_output=True, text=True)
        token = command('begin', '--run-dir', root, '--pid', os.getpid(), '--disposable', 'game').stdout.strip()
        code = 'from pathlib import Path; p=Path(%r); p.mkdir(); (p/"state").write_text("first")' % str(root / 'game')
        command('run', '--run-dir', root, '--lease', token, '--', sys.executable, '-c', code)
        self.assertTrue((root / 'game/state').exists())
        code = 'from pathlib import Path; p=Path(%r); assert p.read_text()=="first"; p.write_text("read")' % str(root / 'game/state')
        command('run', '--run-dir', root, '--lease', token, '--', sys.executable, '-c', code)
        command('finish', '--run-dir', root, '--lease', token, '--exit-code', 0)
        self.assertFalse((root / 'game').exists())

    @unittest.skipIf(os.name == 'nt', 'POSIX isolated-group orphan contract')
    def test_surviving_orphan_child_blocks_finish_after_leader_exits(self):
        root, owner = self.run_dir()
        self.payload(root)
        code = 'import subprocess,sys,time; p=subprocess.Popen([sys.executable,"-c","import time; time.sleep(15)"]); print(p.pid,flush=True); time.sleep(.7)'
        process = subprocess.Popen([sys.executable, '-c', code], start_new_session=True, stdout=subprocess.PIPE, text=True)
        owner.track_process(process)
        try:
            child = int(process.stdout.readline())
            process.wait(timeout=5)
            self.assertEqual(owner.finish(True)['status'], 'active')
            self.assertFalse((root / retention.FINISHED).exists())
            self.assertTrue((root / 'game').exists())
            os.killpg(process.pid, signal.SIGTERM)
            for unused in range(100):
                if retention._quiet(owner.lease):
                    break
                time.sleep(.01)
            owner.finish(True)
            self.assertFalse((root / 'game').exists())
        finally:
            process.stdout.close()
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            process.wait()


if __name__ == '__main__':
    unittest.main()
