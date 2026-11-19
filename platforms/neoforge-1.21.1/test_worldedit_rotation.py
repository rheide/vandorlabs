#!/usr/bin/env python3
"""Run actual WorldEdit clipboard rotations in an isolated NeoForge server."""
import argparse
import hashlib
import os
from pathlib import Path
from shutil import copy2

from native_test_artifacts import artifact_directory, retain_artifacts
from test_sampled_materials import fixture_world
from test_transmitter import ROOT, command, jdk_home, run


@retain_artifacts
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--worldedit-jar', type=Path, help='NeoForge WorldEdit JAR for Minecraft 1.21.1')
    options = parser.parse_args()
    if options.worldedit_jar and not options.worldedit_jar.is_file():
        parser.error('--worldedit-jar must be a file')
    out = Path(artifact_directory(prefix='worldedit-rotation-', dir=ROOT / 'build/test-artifacts'))
    world = out / 'server'
    fixture_world(world, 0)
    mode = 'installed' if options.worldedit_jar else 'absent'
    if options.worldedit_jar:
        mods = world / 'mods'
        mods.mkdir()
        copy2(options.worldedit_jar, mods)
        checksum = hashlib.sha256(options.worldedit_jar.read_bytes()).hexdigest()
        (out / 'worldedit.sha256').write_text(f'{checksum}  {options.worldedit_jar.name}\n')
    env = dict(os.environ, JAVA_HOME=str(jdk_home(21)),
               JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS', '') + ' -Dvandorlabs.worldEditCheck=' + mode)
    run(command('runServer', '-PmodernServerRunDirectory=' + str(world)), out / 'server.log',
        f'[vandorlabs][worldedit-rotation] {mode} PASS', env)


if __name__ == '__main__':
    main()
