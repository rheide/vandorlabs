#!/usr/bin/env python3
"""Test one mod JAR on every released Forge build for Minecraft 1.12.2 from 2860 on."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MAVEN = 'https://maven.minecraftforge.net/net/minecraftforge/forge/'
BASELINE = (14, 23, 5, 2860)


def fetch(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'VandorLabs-ForgeMatrix/1'})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def published_versions():
    metadata = ET.fromstring(fetch(MAVEN + 'maven-metadata.xml'))
    versions = {}
    for entry in metadata.findall('./versioning/versions/version'):
        match = re.fullmatch(r'1\.12\.2-(\d+\.\d+\.\d+\.\d+)', entry.text or '')
        if match:
            key = tuple(map(int, match[1].split('.')))
            if key >= BASELINE:
                versions[key] = match[1]
    if BASELINE not in versions:
        raise RuntimeError('The baseline build is missing from the Forge Maven index')
    return [versions[key] for key in sorted(versions)]


def prepare_runtime(version, server_base, directory):
    artifact = 'forge-1.12.2-' + version + '-universal.jar'
    url = MAVEN + '1.12.2-' + version + '/' + artifact
    jar = directory / artifact
    data = fetch(url)
    if hashlib.sha1(data).hexdigest() != fetch(url + '.sha1').decode().strip().lower():
        raise ValueError('Official Forge artifact digest mismatch for ' + version)
    jar.write_bytes(data)
    with zipfile.ZipFile(jar) as archive:
        manifest = archive.read('META-INF/MANIFEST.MF').decode().replace('\r\n', '\n').replace('\n ', '')
    classpath = next(line for line in manifest.splitlines() if line.startswith('Class-Path: '))
    for name in classpath.split()[1:]:
        # Use precisely the libraries requested by this build, including vanilla.
        relative = Path(name)
        if relative.is_absolute() or relative.drive or '..' in relative.parts or ':' in name:
            raise ValueError('Unexpected Forge manifest classpath entry: ' + name)
        src = server_base / relative
        dst = directory / relative
        if not src.is_file():
            raise FileNotFoundError(f'{version} requires {name}. Install its Forge server libraries first.')
        dst.parent.mkdir(parents=True, exist_ok=True)
        try:
            os.link(src, dst)
        except OSError:
            shutil.copyfile(src, dst)
    return jar


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True, help='Standard mod JAR to test unchanged')
    parser.add_argument('--server-base', type=Path, required=True, help='Installed server libraries and vanilla server JAR')
    parser.add_argument('--java', default='java', help='Java 8 executable')
    args = parser.parse_args()
    artifact = args.jar.resolve()
    failures = []
    versions = published_versions()
    with tempfile.TemporaryDirectory(prefix='vl-forge-matrix-') as temp:
        directory = Path(temp)
        frozen = directory / artifact.name
        shutil.copyfile(artifact, frozen)
        print('Testing ' + hashlib.sha256(frozen.read_bytes()).hexdigest() + ' on ' + ', '.join(versions), flush=True)
        for version in versions:
            install = directory / version
            install.mkdir()
            try:
                forge = prepare_runtime(version, args.server_base, install)
                subprocess.run([sys.executable, str(ROOT / 'testclient/test_server_startup.py'),
                                '--forge', str(forge), '--expected-forge', version, '--java', args.java,
                                '--jar', str(frozen), '--vehicle-world-checks'], check=True)
            except (OSError, ValueError, subprocess.CalledProcessError) as error:
                print(f'Forge {version} FAIL: {error}', file=sys.stderr, flush=True)
                failures.append(version)
            else:
                print(f'Forge {version} PASS', flush=True)
    if failures:
        sys.exit('Failed Forge builds: ' + ', '.join(failures))


if __name__ == '__main__':
    main()
