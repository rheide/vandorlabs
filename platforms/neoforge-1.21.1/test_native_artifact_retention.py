#!/usr/bin/env python3
"""Temporary-directory tests for native launcher lifetime boundaries; starts no game/build."""
import ast
import contextlib
import io
import os
from pathlib import Path
import tempfile
import sys
import unittest
from unittest.mock import patch
import native_test_artifacts as native


class RetentionChecks(unittest.TestCase):
    def fixture(self, base, failure=False):
        @native.retain_artifacts
        def run():
            root = Path(native.artifact_directory(prefix='native-retention-', dir=base))
            runtime = root / 'saved-world'
            (runtime / 'logs').mkdir(parents=True)
            (runtime / 'logs/latest.log').write_text('phase write PASS\n')
            (runtime / 'world').mkdir()
            (runtime / 'world/level.dat').write_bytes(b'owned restart state')
            (root / 'artifact.sha256').write_text('exact artifact hash\n')
            (root / 'report.json').write_text('{"reference":true}\n')

            @native.retain_artifacts
            def restart():
                self.assertTrue((runtime / 'world/level.dat').is_file())
                self.assertEqual((runtime / 'world/level.dat').read_bytes(), b'owned restart state')
                (runtime / 'logs/latest.log').write_text('phase read PASS\n')
            restart()
            self.assertTrue((runtime / 'world/level.dat').is_file())
            client = root / 'client'
            (client / 'screenshots').mkdir(parents=True)
            (client / 'screenshots/proof.png').write_bytes(b'fixture screenshot evidence')
            (client / 'mods').mkdir()
            (client / 'mods/copied-fixture.jar').write_bytes(b'disposable copy')
            self.current = root
            if failure:
                raise ValueError('second process phase refused')
            return root
        return run

    def test_cleanup_after_final_restart_preserves_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(Path(directory))()
            self.assertFalse((root / 'saved-world/world/level.dat').exists())
            self.assertFalse((root / 'client/mods/copied-fixture.jar').exists())
            self.assertEqual((root / 'artifact.sha256').read_text(), 'exact artifact hash\n')
            self.assertTrue((root / 'report.json').is_file())
            self.assertEqual((root / 'evidence/saved-world/logs/latest.log').read_text(), 'phase read PASS\n')
            self.assertTrue((root / 'evidence/client/screenshots/proof.png').is_file())
            self.assertEqual((root / 'saved-world/logs/latest.log').read_text(), 'phase read PASS\n')
            self.assertTrue((root / 'client/screenshots/proof.png').is_file())
            self.assertFalse((root / 'evidence/client/mods/copied-fixture.jar').exists())

    def test_failures_keep_original_error_and_bounded_payloads(self):
        with tempfile.TemporaryDirectory() as directory:
            roots = []
            for _ in range(3):
                with self.assertRaisesRegex(ValueError, 'second process phase refused'):
                    self.fixture(Path(directory), failure=True)()
                roots.append(self.current)
            self.assertFalse((roots[0] / 'saved-world/world/level.dat').exists())
            self.assertLessEqual(sum((root / 'saved-world/world/level.dat').exists() for root in roots), 2)
            for root in roots:
                self.assertTrue((root / 'evidence/saved-world/logs/latest.log').is_file())
                self.assertTrue((root / 'artifact.sha256').is_file())

    def test_keep_environment_opt_out(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {'VANDOR_LABS_TEST_KEEP_ARTIFACTS': '1'}):
            root = self.fixture(Path(directory))()
            self.assertTrue((root / 'saved-world/world/level.dat').is_file())
            self.assertTrue((root / 'client/mods/copied-fixture.jar').is_file())

    def test_real_python_children_use_all_common_launchers(self):
        import test_cube
        import test_matter
        import test_transmitter
        with tempfile.TemporaryDirectory() as directory:
            @native.retain_artifacts
            def run():
                root = Path(native.artifact_directory(prefix='native-children-', dir=directory))
                (root / 'saved-world').mkdir()
                (root / 'saved-world/level.dat').write_bytes(b'restart')
                for index, module in enumerate((test_cube, test_matter, test_transmitter)):
                    process = module.launch([sys.executable, '-c', 'print("python phase PASS")'], root / ('phase%d.log' % index), dict(os.environ))
                    try:
                        self.assertEqual(process.wait(timeout=5), 0)
                    finally:
                        module.stop(process)
                    self.assertTrue((root / 'saved-world/level.dat').is_file())
                return root
            root = run()
            self.assertFalse((root / 'saved-world/level.dat').exists())
            for index in range(3):
                self.assertIn('python phase PASS', (root / ('phase%d.log' % index)).read_text())

    def test_live_python_child_blocks_cleanup_until_stopped(self):
        import test_cube
        with tempfile.TemporaryDirectory() as directory:
            @native.retain_artifacts
            def run():
                root = Path(native.artifact_directory(prefix='native-active-', dir=directory))
                (root / 'saved-world').mkdir()
                (root / 'saved-world/level.dat').write_bytes(b'active')
                self.run_owner = native._CURRENT.get().runs[0][1]
                self.child = test_cube.launch([sys.executable, '-c', 'import time; time.sleep(15)'], root / 'active.log', dict(os.environ))
                return root
            try:
                root = run()
                self.assertTrue((root / 'saved-world/level.dat').is_file())
            finally:
                self.child.terminate()
                self.child.wait(timeout=5)
            self.run_owner.finish(True)
            self.assertFalse((root / 'saved-world/level.dat').exists())

    def test_process_tracking_and_whole_main_status(self):
        calls = []
        class FakeRun:
            def __init__(self, directory, disposable_paths):
                self.directory = directory
                self.disposable_paths = disposable_paths
            def track_process(self, process):
                calls.append(('track', process))
            def finish(self, success, active_processes=()):
                calls.append(('finish', success, active_processes))
        class Active:
            def poll(self):
                return None
        process = Active()
        with tempfile.TemporaryDirectory() as directory, patch.object(native, 'ArtifactRun', FakeRun):
            @native.retain_artifacts
            def run():
                root = Path(native.artifact_directory(prefix='native-', dir=directory))
                self.assertEqual(calls, [])
                self.assertIs(native.track_process(process, root / 'write.log'), process)
                @native.retain_artifacts
                def phase():
                    self.assertEqual(len(calls), 1)
                phase()
                self.assertEqual(len(calls), 1)
            run()
        self.assertEqual(calls, [('track', process), ('finish', True, (process,))])

    def test_finalization_io_error_visible_without_masking_original_failure(self):
        class BrokenRun:
            def __init__(self, directory, disposable_paths):
                pass
            def finish(self, success, active_processes=()):
                raise PermissionError('fixture cleanup permission refused')
        with tempfile.TemporaryDirectory() as directory, patch.object(native, 'ArtifactRun', BrokenRun), contextlib.redirect_stderr(io.StringIO()) as error:
            @native.retain_artifacts
            def fail():
                native.artifact_directory(prefix='native-', dir=directory)
                raise ValueError('original gameplay failure')
            with self.assertRaisesRegex(ValueError, 'original gameplay failure'):
                fail()
            self.assertIn('fixture cleanup permission refused', error.getvalue())
            @native.retain_artifacts
            def succeed():
                native.artifact_directory(prefix='native-', dir=directory)
            with self.assertRaisesRegex(PermissionError, 'fixture cleanup permission refused'):
                succeed()

    def test_unowned_allocation_refused(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(RuntimeError, 'requires @retain_artifacts'):
                native.artifact_directory(prefix='unowned-', dir=directory)
            self.assertEqual(list(Path(directory).iterdir()), [])

    def test_all_native_allocators_have_main_lifetime_wrapper(self):
        platform = Path(__file__).resolve().parent
        wrapped = 0
        for path in platform.glob('test_*.py'):
            tree = ast.parse(path.read_text(), filename=str(path))
            raw = [node for node in ast.walk(tree) if isinstance(node, ast.Call)
                   and isinstance(node.func, ast.Attribute) and node.func.attr == 'mkdtemp']
            self.assertFalse(raw, str(path) + ' bypasses owned artifact allocation')
            allocated = any(isinstance(node, ast.Call) and isinstance(node.func, ast.Name)
                            and node.func.id == 'artifact_directory' for node in ast.walk(tree))
            if not allocated or path == Path(__file__).resolve():
                continue
            main = next(node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name == 'main')
            self.assertTrue(any(isinstance(node, ast.Name) and node.id == 'retain_artifacts' for node in main.decorator_list), str(path))
            wrapped += 1
        self.assertGreaterEqual(wrapped, 28)


if __name__ == '__main__':
    unittest.main()
