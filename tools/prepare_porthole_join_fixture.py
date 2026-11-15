#!/usr/bin/env python3
"""Extract the bounded Porthole regression cells from a supplied Forge world archive.

Requires 7z and nbtlib. Only the selected tiles and held Duplifier are retained.
"""
import argparse
import gzip
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tempfile
import zlib
import nbtlib

FAMILIES = {'vandorlabs:programmable_porthole_wall',
            'vandorlabs:programmable_porthole_block', 'vandorlabs:programmable_glass'}


def nibble(values, index):
    return (int(values[index // 2]) >> ((index & 1) * 4)) & 15


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--world', default='test')
    args = parser.parse_args()
    if not args.world or any(part in ('', '.', '..') for part in args.world.split('/')) or '\\' in args.world or ':' in args.world:
        parser.error('Use the relative world directory inside the archive')
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / 'tiles').mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='porthole-source-', dir=args.out) as temporary:
        subprocess.run(['7z', 'x', '-y', '-o' + temporary, str(args.archive.resolve()),
                        args.world + '/level.dat', args.world + '/region/r.-1.0.mca'],
                       check=True, stdout=subprocess.DEVNULL, timeout=60)
        world = Path(temporary) / args.world
        level = nbtlib.load(world / 'level.dat')
        registry = {int(entry['V']): str(entry['K'])
                    for entry in level['FML']['Registries']['minecraft:blocks']['ids']}
        region = (world / 'region/r.-1.0.mca').read_bytes()
        chunk_x, chunk_z = -11, 9
        index = (chunk_x & 31) + (chunk_z & 31) * 32
        location = int.from_bytes(region[index * 4:index * 4 + 4], 'big')
        offset = (location >> 8) * 4096
        length = int.from_bytes(region[offset:offset + 4], 'big')
        if not offset or length < 1 or offset + 4 + length > len(region):
            raise ValueError('Missing or truncated selected chunk')
        payload, kind = region[offset + 5:offset + 4 + length], region[offset + 4]
        raw = zlib.decompress(payload) if kind == 2 else gzip.decompress(payload) if kind == 1 else payload if kind == 3 else None
        if raw is None:
            raise ValueError('Unsupported chunk compression')
        chunk = nbtlib.File.parse(io.BytesIO(raw))['Level']
        sections = {int(section['Y']): section for section in chunk['Sections']}
        tiles = {tuple(int(tile[key]) for key in ('x', 'y', 'z')): tile
                 for tile in chunk['TileEntities']}
        rows = []
        for x in (-162, -161):
            for y in (83, 84):
                for z in range(148, 155):
                    section = sections[y // 16]
                    index = ((y & 15) * 16 + (z & 15)) * 16 + (x & 15)
                    block_id = (int(section['Blocks'][index]) & 255) + (nibble(section['Add'], index) << 8 if 'Add' in section else 0)
                    name = registry.get(block_id)
                    if name not in FAMILIES:
                        continue
                    tile = tiles[(x, y, z)]
                    meta = nibble(section['Data'], index)
                    rows.append((x, y, z, name, meta))
                    nbtlib.File(tile).save(args.out / 'tiles' / f'{x}_{y}_{z}.dat', gzipped=True)
        if len(rows) != 18 or any(sum(row[3] == family for row in rows) != 6 for family in FAMILIES):
            raise ValueError('Expected the exact six full Portholes, six thin originals and six Glass cells')
        tools = [stack for stack in level['Data']['Player']['Inventory']
                 if str(stack.get('id')) == 'vandorlabs:duplifier']
        if len(tools) != 1:
            raise ValueError('Expected one saved Duplifier')
        nbtlib.File(tools[0]).save(args.out / 'duplifier.dat', gzipped=True)
        (args.out / 'rows.tsv').write_text(''.join('\t'.join(map(str, row)) + '\n' for row in rows))
        (args.out / 'source.json').write_text(json.dumps({
            'archive_sha256': hashlib.sha256(args.archive.read_bytes()).hexdigest(),
            'cells': rows}, indent=2) + '\n')
    print('Prepared exact Porthole scene: 18 cells and one saved Duplifier')


if __name__ == '__main__':
    main()
