#!/usr/bin/env python3
"""Run isolated legacy/modern Gradle processes with their required JDKs."""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
TARGETS = {
    'forge-1.12.2': (ROOT, 8, 'VANDOR_JAVA8_HOME'),
    'neoforge-1.21.1': (ROOT / 'platforms/neoforge-1.21.1', 21, 'VANDOR_JAVA21_HOME'),
}

def java_home(major, variable):
    candidates = [os.environ.get(variable), os.environ.get(f'JAVA{major}_HOME'), os.environ.get('JAVA_HOME')]
    candidates += [f'/usr/lib/jvm/java-{major}-openjdk-amd64', f'/usr/lib/jvm/java-1.{major}.0-openjdk-amd64']
    for value in candidates:
        if not value:
            continue
        executable = Path(value) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
        compiler = Path(value) / 'bin' / ('javac.exe' if os.name == 'nt' else 'javac')
        if not executable.is_file() or not compiler.is_file():
            continue
        result = subprocess.run([str(executable), '-version'], capture_output=True, text=True, check=True)
        version = re.search(r'version "([^\"]+)', result.stderr + result.stdout)
        if version:
            parts = version.group(1).split('.')
            actual = int(parts[1] if parts[0] == '1' else parts[0])
            if actual == major:
                return str(Path(value).resolve())
    raise RuntimeError(f'Java {major} required; set {variable} to its JDK directory')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target', action='append', choices=list(TARGETS), help='Build only named target(s); default is both')
    parser.add_argument('--verify-legacy-release', action='store_true', help='Run the existing full Forge release gate instead of build')
    args = parser.parse_args()
    selected = args.target or list(TARGETS)
    # Resolve all JDKs before starting any build.
    homes = {name: java_home(TARGETS[name][1], TARGETS[name][2]) for name in selected}
    version_match = re.search(r"^version\s*=\s*'([^']+)'", (ROOT / 'build.gradle').read_text(), re.MULTILINE)
    if not version_match:
        raise RuntimeError('Cannot determine root mod version')
    version = version_match.group(1)
    fixture_env = dict(os.environ)
    for name in selected:
        fixture_env[f'JAVA{TARGETS[name][1]}_HOME'] = homes[name]
    subprocess.run([sys.executable, str(ROOT / 'tools/test_portable.py')], cwd=ROOT, env=fixture_env, check=True)
    artifacts = []
    for name in selected:
        directory, _, _ = TARGETS[name]
        env = dict(os.environ, JAVA_HOME=homes[name])
        env['PATH'] = str(Path(homes[name]) / 'bin') + os.pathsep + env.get('PATH', '')
        wrapper = directory / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
        task = 'verifyRelease' if name == 'forge-1.12.2' and args.verify_legacy_release else 'build'
        print(f'Building {name} with Java {TARGETS[name][1]} ({task})', flush=True)
        command = [str(wrapper), task, '--no-daemon', '-PpythonExecutable=' + sys.executable]
        if os.name == 'nt':
            # cmd /s strips the outside pair; retain an individually quoted wrapper
            # and arguments, including JDK/Python paths containing spaces.
            command = [os.environ.get('COMSPEC', 'cmd.exe'), '/d', '/v:off', '/s', '/c',
                       '"' + ' '.join('"' + argument + '"' for argument in command) + '"']
        subprocess.run(command, cwd=directory, env=env, check=True)
        source = directory / 'build/libs' / (f'vandorlabs-{version}.jar' if name == 'forge-1.12.2' else f'vandorlabs-{name}-{version}.jar')
        if not source.is_file():
            raise RuntimeError(f'Expected standard artifact missing: {source}')
        artifacts.append((source, ROOT / f'build/libs/vandorlabs-{name}-{version}.jar'))
    # Publish the selected artifact set only after every requested build succeeds.
    for source, destination in artifacts:
        destination.parent.mkdir(parents=True, exist_ok=True)
        if source.resolve() != destination.resolve():
            shutil.copy2(source, destination)
        print(f'Built {destination.relative_to(ROOT)}', flush=True)
    print('Build completion does not establish live-client or feature parity acceptance.', flush=True)

if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError) as error:
        print(f'Build failed: {error}', file=sys.stderr)
        sys.exit(1)
