#!/usr/bin/env python3
"""Fake launchers test the release scheduler; never starts Minecraft or Gradle."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import patch

import release_live_checks as live


def fake_child():
    name, mode, event_dir = sys.argv[2:5]
    event = Path(event_dir) / (name + '.json')
    start = time.monotonic()
    artifact = Path(os.environ['VANDOR_LABS_TEST_RUN_ROOT']) / 'artifact.sha256'
    if mode != 'no-hash':
        artifact.write_text(('0' * 64 if mode == 'bad-hash' else live.sha256(os.environ['VANDOR_LABS_TEST_JAR'])) + '  fixture.jar\n')
        if name in ('mekanism-tanks', 'navigation-vehicle', 'maxwell-cruise'):
            second = artifact.parent / 'verify'; second.mkdir()
            (second / 'artifact.sha256').write_text(artifact.read_text())
    row = dict(start=start, env={k: os.environ[k] for k in (
        'VANDOR_LABS_TEST_SKIP_BUILD', 'VANDOR_LABS_TEST_JAR', 'VANDOR_LABS_TEST_OPTIFINE',
        'VANDOR_LABS_TEST_SHADERPACK', 'VANDOR_LABS_TEST_RUN_ROOT', 'VANDOR_LABS_TEST_GAME_DIR')})
    event.write_text(json.dumps(row))
    if mode == 'tree':
        # A nested session models test_artifacts.py -> Xvfb/JVM, outside the launcher group.
        child = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(30)'], start_new_session=os.name != 'nt')
        row['child'] = child.pid; event.write_text(json.dumps(row))
        time.sleep(30)
    else:
        time.sleep(.04 if mode in ('fail', 'mutate') else .14)
    if mode == 'mutate':
        with open(os.environ['VANDOR_LABS_TEST_JAR'], 'ab') as jar:
            jar.write(b'changed')
    row['end'] = time.monotonic(); event.write_text(json.dumps(row))
    return 7 if mode == 'fail' else 0


class SchedulerChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='release-scheduler-')
        self.root = Path(self.temp.name)
        self.jar = self.root / 'candidate.jar'; self.jar.write_bytes(b'frozen jar')
        self.events = self.root / 'events'; self.events.mkdir()

    def tearDown(self):
        self.temp.cleanup()

    def run_plan(self, names, modes=None, jobs=2, stop=None, scope=None):
        modes = modes or {}
        output = self.root / ('run-' + str(len(list(self.root.glob('run-*')))))
        def command(name, jar, heap):
            return [sys.executable, str(Path(__file__).resolve()), '--child', name, modes.get(name, 'ok'), str(self.events)]
        return live.run_release(self.jar, output, jobs, names=names, builder=command,
                                stop=stop, grace=.3, report_path=self.root / 'summary.json', scope=scope)

    def test_two_lanes_exclusive_barriers_and_isolation(self):
        names = ['a', 'b', 'vehicle-render-cost', 'render-density',
                 'observer-performance', 'navigation-performance', 'c', 'd']
        report = self.run_plan(names)
        self.assertEqual('passed', report['status'])
        rows = {name: json.loads((self.events / (name + '.json')).read_text()) for name in names}
        boundaries = sorted([(r['start'], 1) for r in rows.values()] + [(r['end'], -1) for r in rows.values()])
        count = maximum = 0
        for at, delta in boundaries:
            count += delta; maximum = max(maximum, count)
        self.assertEqual(2, maximum)
        for exclusive in live.EXCLUSIVE:
            a = rows[exclusive]
            self.assertTrue(all(name == exclusive or r['end'] <= a['start'] or r['start'] >= a['end'] for name, r in rows.items()))
        self.assertEqual(len(names), len({r['env']['VANDOR_LABS_TEST_GAME_DIR'] for r in rows.values()}))
        for row in rows.values():
            self.assertEqual('true', row['env']['VANDOR_LABS_TEST_SKIP_BUILD'])
            self.assertEqual('', row['env']['VANDOR_LABS_TEST_OPTIFINE'])
            self.assertEqual('', row['env']['VANDOR_LABS_TEST_SHADERPACK'])
            self.assertEqual(str(self.jar), row['env']['VANDOR_LABS_TEST_JAR'])
        self.assertFalse(report['full_release_coverage'])
        self.assertEqual(report, json.loads((self.root / 'summary.json').read_text()))

    def test_collects_failures_and_complete_coverage(self):
        report = self.run_plan(['a', 'b', 'c', 'd'], {'a': 'fail', 'c': 'fail'})
        self.assertEqual('failed', report['status'])
        self.assertEqual({'a', 'c'}, set(report['failures']))
        self.assertEqual([], report['not_run'])
        self.assertTrue(report['complete_coverage'])
        self.assertEqual({'a', 'b', 'c', 'd'}, {r['focus'] for r in report['results']})

    def test_jar_mutation_stops_invalidated_scheduling(self):
        report = self.run_plan(['a', 'b', 'c'], {'a': 'mutate'})
        self.assertEqual('failed', report['status'])
        self.assertEqual(['c'], report['not_run'])
        self.assertNotEqual(report['sha256_start'], report['sha256_end'])
        self.assertIsNotNone(report['infrastructure_error'])

    def test_fixture_hash_mismatch_is_not_a_pass(self):
        report = self.run_plan(['a', 'b', 'c'], {'a': 'bad-hash'})
        self.assertEqual('failed', report['status'])
        self.assertEqual(['c'], report['not_run'])
        self.assertIn('Fixture JAR hash mismatch', report['infrastructure_error'])

    def test_success_without_fixture_hash_is_not_a_pass(self):
        report = self.run_plan(['a', 'b', 'c'], {'a': 'no-hash'})
        self.assertEqual('failed', report['status'])
        self.assertEqual(['c'], report['not_run'])
        self.assertIn('Missing fixture JAR hashes', report['infrastructure_error'])

    def test_full_inventory_and_platform_commands(self):
        self.assertEqual(set(live.RELEASE_FOCI), set(live.PRIORITY))
        self.assertEqual(set(live.LEGACY_FOCI) | {
            'navigation-vehicle', 'navigation-world', 'navigation-module', 'module-rack',
            'appliances', 'synthesis-tower', 'maxwell-drive', 'vehicle-status', 'maxwell-cruise', 'vehicle-resources', 'infuser-gui', 'ramp-dialog', 'core-icons',
            'observer-performance', 'navigation-performance'}, set(live.PRIORITY))
        report = self.run_plan(live.PRIORITY, jobs=2, scope='full')
        self.assertEqual('passed', report['status'])
        self.assertEqual(len(live.RELEASE_FOCI) + sum(bool(os.environ.get(v)) for v in live.OPTIONAL_FOCI.values()), len(report['results']))
        self.assertTrue(report['full_release_coverage'])
        self.assertIn('--shot-prefix', live.command_for('utility22_card_mounts', self.jar, '8G', windows=True))
        for name in live.RELEASE_FOCI:
            command = live.command_for(name, self.jar, '8G', windows=True)
            self.assertIn(str(self.jar), command)
            self.assertIn('testclient/test_navigation_vehicle.py' if name == 'navigation-vehicle' else name, command)
            self.assertEqual('8G', command[command.index('--max-heap') + 1])
        # Preserve the original platform-specific optional coverage: Linux's wrapper
        # performs save -> fresh-JVM verify; Windows retains its existing single focus.
        self.assertEqual(['bash', 'testclient/test_mekanism_tank.sh'], live.command_for('mekanism-tanks', self.jar, '8G', windows=False))
        self.assertIn('mekanism-tanks', live.command_for('mekanism-tanks', self.jar, '8G', windows=True))

    def test_jei_is_optional_and_omission_is_reported(self):
        with patch.dict(os.environ, {'VANDOR_LABS_TEST_JEI': ''}):
            report = self.run_plan(live.PRIORITY, scope='full')
        self.assertNotIn('machine-jei', report['expected'])
        self.assertIn('machine-jei', {row['focus'] for row in report['optional_skipped']})
        with patch.dict(os.environ, {'VANDOR_LABS_TEST_JEI': str(self.jar)}):
            report = self.run_plan(live.PRIORITY, scope='full')
        self.assertIn('machine-jei', report['expected'])
        self.assertNotIn('machine-jei', {row['focus'] for row in report['optional_skipped']})
        self.assertEqual('passed', report['status'])

    def test_new_windows_focus_selectors_and_dispatch_cover_release_inventory(self):
        # Parse the actual CLI and dispatch tables without starting Windows/Java.
        import ast
        tree = ast.parse((live.ROOT / 'testclient/run_windows.py').read_text())
        choices = next(ast.literal_eval(k.value) for node in ast.walk(tree)
                       if isinstance(node, ast.Call) and node.args and
                       isinstance(node.args[0], ast.Constant) and node.args[0].value == '--focus'
                       for k in node.keywords if k.arg == 'choices')
        required = set(live.RELEASE_FOCI) - {'utility22_card_mounts'} | {'machine-jei'}
        self.assertTrue(required.issubset(choices))
        tables = [{key.value for key in node.keys if isinstance(key, ast.Constant)}
                  for node in ast.walk(tree) if isinstance(node, ast.Dict)]
        self.assertGreaterEqual(sum(required.issubset(keys) for keys in tables), 2,
                                'Windows property and completion-marker dispatch must both cover every focus')

    def test_navigation_wrapper_keeps_saved_world_through_both_phases(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location('navigation_roundtrip', live.ROOT / 'testclient/test_navigation_vehicle.py')
        navigation = importlib.util.module_from_spec(spec); spec.loader.exec_module(navigation)
        child = self.root / 'navigation_child.py'
        child.write_text('''import hashlib,os
from pathlib import Path
game=Path(os.environ['VANDOR_LABS_TEST_GAME_DIR'])
phase=os.environ['VANDOR_LABS_NAVIGATION_VEHICLE_PHASE']
saved=game/'saves/navigation-vehicle/level.dat'
if phase=='write':saved.parent.mkdir(parents=True);saved.write_bytes(b'owned navigation state')
else:assert saved.read_bytes()==b'owned navigation state'
output=Path(os.environ['VANDOR_LABS_TEST_RUN_ROOT'])/phase;output.mkdir(parents=True)
(output/'artifact.sha256').write_text(hashlib.sha256(Path(os.environ['VANDOR_LABS_TEST_JAR']).read_bytes()).hexdigest()+'  candidate.jar\\n')
''')
        with patch.dict(os.environ, {'VANDOR_LABS_TEST_RUN_ROOT': str(self.root / 'navigation'),
                                    'VANDOR_LABS_TEST_KEEP_ARTIFACTS': '0'}):
            output = navigation.run_phases(self.jar, '8G', root=self.root,
                                          launcher=[sys.executable, str(child)])
        self.assertEqual(2, len(live.artifact_hashes(output / 'artifacts', live.sha256(self.jar))))
        self.assertFalse((output / 'game').exists())
        self.assertEqual('pruned', json.loads((output / 'retention.json').read_text())['status'])

    @unittest.skipIf(os.name == 'nt', 'Linux legacy two-phase wrapper')
    def test_mekanism_wrapper_keeps_world_until_verify_then_finalizes(self):
        repo = self.root / 'fake-repo'; (repo / 'testclient').mkdir(parents=True); (repo / 'tools').mkdir()
        shutil.copyfile(live.ROOT / 'testclient/test_mekanism_tank.sh', repo / 'testclient/test_mekanism_tank.sh')
        shutil.copyfile(live.ROOT / 'tools/test_artifacts.py', repo / 'tools/test_artifacts.py')
        (repo / 'testclient/test_viewscreen.sh').write_text('''#!/bin/bash
set -euo pipefail
test "$VANDOR_LABS_TEST_SKIP_BUILD" = true
mkdir -p "$VANDOR_LABS_TEST_GAME_DIR"
if [ "$VANDOR_LABS_MEKANISM_TANK_RESTART" = save ]; then
  echo 37 > "$VANDOR_LABS_TEST_GAME_DIR/charge"
else
  test "$(cat "$VANDOR_LABS_TEST_GAME_DIR/charge")" = 37
fi
echo "$VANDOR_LABS_MEKANISM_TANK_RESTART" >> "$VANDOR_LABS_TEST_RUN_ROOT/order"
''')
        output = self.root / 'mekanism'
        subprocess.run(['bash', str(repo / 'testclient/test_mekanism_tank.sh')], check=True,
                       env=dict(os.environ, VANDOR_LABS_TEST_RUN_ROOT=str(output),
                                VANDOR_LABS_TEST_MEKANISM='fake.jar', VANDOR_LABS_TEST_KEEP_ARTIFACTS='0'),
                       stdout=subprocess.DEVNULL)
        self.assertEqual(['save', 'verify'], (output / 'order').read_text().splitlines())
        fixture = next(output.glob('mekanism-restart.*'))
        self.assertFalse((fixture / 'game').exists())
        self.assertEqual('pruned', json.loads((fixture / 'retention.json').read_text())['status'])

    @unittest.skipIf(os.name == 'nt', 'POSIX nested-session cleanup; Windows uses an owned job')
    def test_cancel_stops_only_owned_nested_processes(self):
        sentinel = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(30)'])
        stop = threading.Event(); timer = threading.Timer(.8, stop.set)
        try:
            timer.start()
            report = self.run_plan(['a', 'b', 'c'], {'a': 'tree', 'b': 'tree'}, stop=stop)
            self.assertEqual('failed', report['status'])
            self.assertEqual(['c'], report['not_run'])
            self.assertIsNone(sentinel.poll(), 'Unrelated process was touched')
            from test_artifacts import _processes
            table = _processes()
            for name in ('a', 'b'):
                pid = json.loads((self.events / (name + '.json')).read_text())['child']
                self.assertTrue(pid not in table or table[pid][3] == 'Z', 'Owned nested child survived')
        finally:
            timer.cancel(); sentinel.terminate(); sentinel.wait()


if __name__ == '__main__':
    if len(sys.argv) > 1 and sys.argv[1] == '--child':
        sys.exit(fake_child())
    unittest.main()
