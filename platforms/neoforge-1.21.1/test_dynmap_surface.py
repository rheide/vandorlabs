#!/usr/bin/env python3
"""Render the exact native JAR's static Surface mappings in an isolated Dynmap server."""
import argparse
from collections import Counter
import hashlib
import os
from pathlib import Path
from shutil import copy2
import secrets
import socket
import struct
import time
from zipfile import ZipFile

from PIL import Image

from native_test_artifacts import artifact_directory, retain_artifacts
from test_cube import PLATFORM, ROOT, command, jdk_home, launch, stop
from test_sampled_materials import fixture_world


ARTWORK = {
    'up-facing Surface': 'turntable/turntable_block_top_low',
    'down-facing Surface side': 'turntable/turntable_block_bottom',
    'Copy Controller': 'copy_controller/top',
}


def wait_for(process, log, marker, seconds):
    deadline = time.monotonic() + seconds
    while marker not in log.read_text(errors='replace'):
        if process.poll() is not None or time.monotonic() > deadline:
            raise RuntimeError(f'Missing {marker!r} in {log}')
        time.sleep(.2)


def rcon(port, password, command_text):
    def packet(stream, identifier, kind, payload):
        data = struct.pack('<ii', identifier, kind) + payload.encode() + b'\0\0'
        stream.write(struct.pack('<i', len(data)) + data)
        stream.flush()

    def response(stream):
        size, = struct.unpack('<i', stream.read(4))
        if not 10 <= size <= 16384:
            raise RuntimeError(f'Unexpected RCON packet length {size}')
        body = stream.read(size)
        identifier, _ = struct.unpack('<ii', body[:8])
        return identifier, body[8:-2].decode(errors='replace')

    with socket.create_connection(('127.0.0.1', port), timeout=15) as connection:
        with connection.makefile('rwb') as stream:
            packet(stream, 1, 3, password)
            if response(stream)[0] != 1:
                raise RuntimeError('Isolated RCON authentication failed')
            packet(stream, 2, 2, command_text)
            identifier, message = response(stream)
            if identifier != 2:
                raise RuntimeError('Unexpected RCON response')
            return message


def verify_artwork(production, tiles):
    palettes = {}
    with ZipFile(production) as jar:
        for role, texture in ARTWORK.items():
            with jar.open(f'assets/vandorlabs/textures/blocks/{texture}.png') as stream:
                with Image.open(stream) as image:
                    palettes[role] = set(image.convert('RGB').getdata())
    counts = Counter()
    for tile in tiles:
        with Image.open(tile) as image:
            counts.update(image.convert('RGB').getdata())
    result = {}
    for role, colors in palettes.items():
        other = set.union(*(palette for key, palette in palettes.items() if key != role))
        unique = colors - other
        matches = sum(counts[pixel] for pixel in unique)
        if matches < 20:
            raise RuntimeError(f'Dynmap rendered too few exclusive {role} palette pixels: {matches}')
        result[role] = matches
    return result


@retain_artifacts
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--jar', type=Path, required=True, help='Exact native Vandor Labs JAR')
    parser.add_argument('--dynmap-jar', type=Path, required=True, help='Experimental Dynmap NeoForge 1.21.1 JAR')
    args = parser.parse_args()
    if not args.jar.is_file() or not args.dynmap_jar.is_file():
        parser.error('both exact artifacts must exist')

    out = Path(artifact_directory(prefix='dynmap-surface-native-', dir=ROOT / 'build/test-artifacts'))
    game = out / 'server'
    fixture_world(game, 0)
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        port = listener.getsockname()[1]
    password = secrets.token_hex(16)
    with (game / 'server.properties').open('a') as properties:
        properties.write(f'enable-rcon=true\nrcon.port={port}\nrcon.password={password}\n')
    mods = game / 'mods'
    mods.mkdir()
    production = mods / args.jar.name
    dynmap = mods / args.dynmap_jar.name
    copy2(args.jar, production)
    copy2(args.dynmap_jar, dynmap)
    sha = hashlib.sha256(production.read_bytes()).hexdigest()
    deps = {production.name: sha, dynmap.name: hashlib.sha256(dynmap.read_bytes()).hexdigest()}
    (out / 'artifacts.sha256').write_text(''.join(f'{digest}  {name}\n' for name, digest in deps.items()))
    dynmap_data = game / 'dynmap'
    dynmap_data.mkdir()
    with ZipFile(dynmap) as jar:
        config = jar.read('configuration.txt').decode()
    config = config.replace('disable-webserver: false', 'disable-webserver: true').replace('image-format: jpg-q90', 'image-format: png')
    (dynmap_data / 'configuration.txt').write_text(config)

    env = dict(os.environ, JAVA_HOME=str(jdk_home(21)))
    env.pop('MOD_CLASSES', None)
    launch_args = command('runServer', '-PmodernServerRunDirectory=' + str(game),
                          '-PcopyJar=' + str(production), '-PcopySha=' + sha)
    log = out / 'server.log'
    process = launch(launch_args, log, env)
    try:
        wait_for(process, log, 'Done (', 120)
        rcon(port, password, 'forceload add 0 0 31 15')
        for region, block in [
            ('1 10 1 7 10 7', 'turntable_surface[facing=up]'),
            ('9 10 1 15 10 7', 'turntable_surface[facing=down]'),
            ('17 10 1 23 10 7', 'copy_controller[facing=north]'),
        ]:
            reply = rcon(port, password, f'fill {region} vandorlabs:{block}')
            if '49 block' not in reply:
                raise RuntimeError(f'Could not place live fixture {block}: {reply}')
        rcon(port, password, 'save-all flush')
        reply = rcon(port, password, 'dynmap radiusrender world 16 4 40 flat')
        (out / 'render-command.txt').write_text(reply + '\n')
        deadline = time.monotonic() + 120
        while True:
            tiles = sorted((dynmap_data / 'web/tiles').glob('**/flat/**/*.png'))
            if tiles:
                try:
                    matches = verify_artwork(production, tiles)
                    break
                except (OSError, RuntimeError):
                    if time.monotonic() > deadline:
                        raise
            if process.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError(f'Dynmap produced no verified flat PNG tiles: {log}')
            time.sleep(1)
        for role, matched in matches.items():
            print(f'{role}: {matched} pixels from its exclusive native texture palette', flush=True)
        for path in (production, dynmap):
            if hashlib.sha256(path.read_bytes()).hexdigest() != deps[path.name]:
                raise RuntimeError(f'Artifact changed during render: {path.name}')
        rcon(port, password, 'stop')
        if process.wait(timeout=40):
            raise RuntimeError(f'Native Dynmap server exited with an error: {log}')
    finally:
        stop(process)
    print(f'Native Dynmap Surface fallback PASS: {out}', flush=True)


if __name__ == '__main__':
    main()
