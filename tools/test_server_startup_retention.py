#!/usr/bin/env python3
"""Runner lifecycle only; no Minecraft startup or gameplay acceptance."""
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import test_artifacts

spec = importlib.util.spec_from_file_location(
    'server_startup', Path(__file__).resolve().parents[1] / 'testclient/test_server_startup.py')
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class StartupRetentionChecks(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='startup retention ')
        self.root = Path(self.temporary.name)
        self.environment = patch.dict(os.environ, {test_artifacts.KEEP_ENV: ''})
        self.environment.start()

    def tearDown(self):
        self.environment.stop()
        self.temporary.cleanup()

    def launch(self, callback, extra=()):
        with patch.object(runner, '__file__', str(self.root / 'testclient/runner.py')), \
                patch('sys.argv', ['runner.py', '--forge', 'fixture.jar', '--jar', 'mod.jar', *extra]), \
                patch.object(runner, 'run_server', side_effect=callback), \
                contextlib.redirect_stdout(io.StringIO()):
            runner.main()

    def test_direct_run_preserves_all_phases_until_suite_finishes(self):
        observed = []

        def suite(args, root, output, owner):
            self.assertIsNotNone(owner)
            world = output / 'world.dat'
            world.write_bytes(b'owned fixture payload')
            for phase in ('prepare', 'place', 'reload'):
                self.assertTrue(world.exists())
                (output / (phase + '.log')).write_text(phase + ' PASS\n')
            observed.append(output)

        self.launch(suite)
        output = observed[0]
        self.assertFalse((output / "world.dat").exists())
        for phase in ('prepare', 'place', 'reload'):
            self.assertEqual((output.parent / 'evidence/game' / (phase + '.log')).read_text(),
                             phase + ' PASS\n')

    def test_failed_suite_keeps_bounded_debug_payload_and_failure(self):
        observed = []

        def suite(args, root, output, owner):
            (output / 'world.dat').write_bytes(b'failed fixture payload')
            (output / 'prepare.log').write_text('failed startup\n')
            observed.append(output)
            raise RuntimeError('original startup failure')

        with self.assertRaisesRegex(RuntimeError, 'original startup failure'):
            self.launch(suite)
        self.assertTrue((observed[0] / 'world.dat').exists())
        self.assertTrue((observed[0].parent / test_artifacts.FINISHED).exists())

    def test_outer_suite_retains_ownership_until_its_later_phases(self):
        parent = self.root / 'outer-game'
        observed = []

        def suite(args, root, output, owner):
            self.assertIsNone(owner)
            (output / 'world.dat').write_bytes(b'outer suite still needs this')
            observed.append(output)

        self.launch(suite, ('--run-root', str(parent)))
        self.assertEqual(observed[0].parent, parent)
        self.assertTrue((observed[0] / 'world.dat').exists())
        self.assertFalse((observed[0] / test_artifacts.MARKER).exists())


if __name__ == '__main__':
    unittest.main()
