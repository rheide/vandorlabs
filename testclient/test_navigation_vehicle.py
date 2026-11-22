#!/usr/bin/env python3
"""Keep capture and cold reload in one retained Linux/Windows vehicle fixture."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from test_artifacts import ArtifactRun


def run_phases(jar, heap, *, root=ROOT, launcher=None, focus='navigation-vehicle'):
    run_root = Path(os.environ.get('VANDOR_LABS_TEST_RUN_ROOT', str(root / 'testclient'))).resolve()
    run_root.mkdir(parents=True, exist_ok=True)
    fixture = Path(tempfile.mkdtemp(prefix=focus+'.', dir=str(run_root)))
    owner = ArtifactRun(fixture, ['game'])
    passed = False
    try:
        env = dict(os.environ, VANDOR_LABS_TEST_SKIP_BUILD='true',
                   VANDOR_LABS_TEST_GAME_DIR=str(fixture / 'game'),
                   VANDOR_LABS_TEST_RUN_ROOT=str(fixture / 'artifacts'),
                   VANDOR_LABS_TEST_JAR=str(jar), VANDOR_LABS_TEST_HEAP=heap)
        for phase in ('write', 'read'):
            phase_name = 'VANDOR_LABS_CRUISE_PHASE' if focus == 'maxwell-cruise' else 'VANDOR_LABS_NAVIGATION_VEHICLE_PHASE'
            env[phase_name] = phase
            command = launcher if launcher is not None else (
                [sys.executable, 'testclient/run_windows.py', '--jar', str(jar),
                 '--max-heap', heap, '--focus', focus] if os.name == 'nt'
                else ['bash', 'testclient/test_viewscreen.sh', '--focus', focus])
            with (fixture / (phase + '.log')).open('w', encoding='utf-8') as log:
                process = owner.track_process(subprocess.Popen(command, cwd=root, env=env,
                                                               stdout=log, stderr=subprocess.STDOUT))
                result = process.wait()
            if result:
                raise subprocess.CalledProcessError(result, command)
        print(focus+' cold roundtrip passed: ' + str(fixture))
        passed = True
        return fixture
    finally:
        owner.finish(passed)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, default=os.environ.get('VANDOR_LABS_TEST_JAR'))
    parser.add_argument('--max-heap', default=os.environ.get('VANDOR_LABS_TEST_HEAP', '8G'))
    parser.add_argument('--focus', choices=('navigation-vehicle', 'maxwell-cruise'), default='navigation-vehicle')
    args = parser.parse_args()
    if args.jar is None:
        version = re.search(r"^version\s*=\s*['\"]([^'\"]+)['\"]", (ROOT / 'build.gradle').read_text(), re.M).group(1)
        args.jar = ROOT / 'build/libs' / ('vandorlabs-' + version + '.jar')
    if not args.jar.is_file():
        parser.error('Pass --jar or set VANDOR_LABS_TEST_JAR to the frozen candidate')
    run_phases(args.jar.resolve(), args.max_heap, focus=args.focus)


if __name__ == '__main__':
    main()
