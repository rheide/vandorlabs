#!/usr/bin/env python3
"""Start the exact standard JAR with a real OptiFine shaderpack on the platform client."""
import argparse
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--optifine', type=Path, default=os.environ.get('VANDOR_LABS_TEST_OPTIFINE'))
p.add_argument('--shaderpack', type=Path, default=os.environ.get('VANDOR_LABS_TEST_SHADERPACK'))
args = p.parse_args()
for name, path in (('OptiFine', args.optifine), ('shaderpack', args.shaderpack)):
    if path is None or not path.is_file():
        p.error(f'{name} is required; set VANDOR_LABS_TEST_OPTIFINE and VANDOR_LABS_TEST_SHADERPACK')
env = dict(os.environ, VANDOR_LABS_TEST_OPTIFINE=str(args.optifine.resolve()),
           VANDOR_LABS_TEST_SHADERPACK=str(args.shaderpack.resolve()), VANDOR_LABS_TEST_SKIP_BUILD='true',
           VANDOR_LABS_TEST_HEAP=os.environ.get('VANDOR_LABS_TEST_HEAP', '8G'))
command = ([sys.executable, 'testclient/run_windows.py', '--focus', 'shader-startup', '--timeout', '900']
           if os.name == 'nt' else ['bash', 'testclient/test_viewscreen.sh', '--focus', 'shader-startup'])
if os.name == 'nt' and env.get('VANDOR_LABS_TEST_JAR'):
    command += ['--jar', env['VANDOR_LABS_TEST_JAR']]
subprocess.run(command, cwd=ROOT, env=env, check=True)
