#!/usr/bin/env python3
"""Exercise the real collect-first shell wrapper without Minecraft or Gradle."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('verify_release.sh')


class ReleaseInventoryTests(unittest.TestCase):
    def run_gate(self, *, cpu_fail=False, live_fail=False, mutate=False, build=False):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'tools').mkdir()
            shutil.copyfile(SCRIPT, root / 'tools/verify_release.sh')
            (root / 'build/libs').mkdir(parents=True)
            jar = root / 'build/libs/vandorlabs-2.10.0.jar'
            jar.write_bytes(b'frozen candidate')
            (root / 'build.gradle').write_text("version = '2.10.0'\n")
            (root / 'jdk/bin').mkdir(parents=True)
            for name in ('java', 'javac'):
                executable = root / 'jdk/bin' / name
                executable.write_text('#!/bin/sh\necho \'java version "1.8.0_fake"\' >&2\n')
                executable.chmod(0o755)
            # This is an entirely fake release gate; the real display executable
            # must never be needed or invoked by these scheduler unit fixtures.
            (root / 'bin').mkdir()
            display = root / 'bin/xvfb-run'
            display.write_text('#!/bin/sh\necho "Unexpected live display invocation" >&2\nexit 99\n')
            display.chmod(0o755)
            mods = root / 'mods'
            mods.mkdir()
            fixture = mods / 'fixture.jar'
            fixture.write_bytes(b'fixture')
            (root / 'tools/generate_texture_browser.py').write_text('print("catalog PASS")\n')
            gradle = root / 'gradlew'
            gradle.write_text('''#!/bin/sh
if [ "$1" = assemble ]; then echo assemble >> assemble-count; exit 0; fi
touch cpu-ran
if [ "$MOCK_CPU_FAIL" = 1 ]; then echo '> Task :testFixture FAILED'; exit 1; fi
echo 'CPU PASS'
''')
            gradle.chmod(0o755)
            (root / 'tools/release_live_checks.py').write_text('''import hashlib,json,os,sys
from pathlib import Path
assert Path('cpu-ran').is_file()
Path('live-ran').touch()
report=Path(sys.argv[sys.argv.index('--report')+1])
failed=os.environ['MOCK_LIVE_FAIL']=='1'
report.write_text(json.dumps({'scope':'full','status':'failed' if failed else 'passed','not_run':[],'infrastructure_error':None,'failures':['fixture'] if failed else []}))
if os.environ['MOCK_MUTATE']=='1':Path(sys.argv[sys.argv.index('--jar')+1]).write_bytes(b'changed')
sys.exit(1 if failed else 0)
''')
            env = dict(os.environ, JAVA_HOME=str(root / 'jdk'),
                       PATH=str(root / 'bin') + os.pathsep + os.environ['PATH'],
                       VANDOR_LABS_COMPAT_MODS=str(mods),
                       MOCK_CPU_FAIL=str(int(cpu_fail)), MOCK_LIVE_FAIL=str(int(live_fail)),
                       MOCK_MUTATE=str(int(mutate)))
            for name in ('OPTIFINE', 'SHADERPACK', 'MEKANISM', 'FORGE'):
                env['VANDOR_LABS_TEST_' + name] = str(fixture)
            command = ['bash', 'tools/verify_release.sh', '--skip-dynmap', '--jobs', '2']
            if not build:
                command += ['--jar', str(jar)]
            result = subprocess.run(command, cwd=root, env=env, capture_output=True, text=True)
            reports = list((root / 'build/release-checks').glob('run.*/report.json'))
            report = json.loads(reports[0].read_text()) if reports else None
            return result, report, (root / 'live-ran').exists(), (
                (root / 'assemble-count').read_text().splitlines() if (root / 'assemble-count').exists() else [])

    def test_clean_inventory_is_final_gate(self):
        result, report, live, builds = self.run_gate()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue(live)
        self.assertEqual(report['status'], 'passed')
        self.assertEqual(builds, [])

    def test_cpu_failure_does_not_hide_live_results(self):
        result, report, live, _ = self.run_gate(cpu_fail=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue(live)
        self.assertEqual(report['cpu_failed_tasks'], ['testFixture'])
        self.assertEqual(report['phases']['live'], 0)

    def test_live_failure_cannot_pass(self):
        result, report, live, _ = self.run_gate(live_fail=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue(live)
        self.assertEqual(report['live_failures'], ['fixture'])

    def test_mutated_candidate_cannot_pass(self):
        result, report, live, _ = self.run_gate(mutate=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue(live)
        self.assertIsNone(report)

    def test_default_builds_once(self):
        result, _, _, builds = self.run_gate(build=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(builds, ['assemble'])


if __name__ == '__main__':
    unittest.main()
