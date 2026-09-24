#!/usr/bin/env python3
"""Install the supplied voxel wall pack under the Vandor Labs namespace."""
import argparse
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/vandorlabs'
TEXTURES = ROOT / 'texture-packs'
PREFIX = 'scifi_voxel_wall_pack/resources/assets/scifidoors/'
OLD = ('wall_regular', 'wall_porthole', 'wall_bottom_diagonal', 'wall_top_diagonal')


def main(archive):
    with zipfile.ZipFile(archive) as source:
        names = source.namelist()
        for name in names:
            if not name.startswith(PREFIX):
                continue
            relative = name[len(PREFIX):]
            if not relative or name.endswith('/') or relative == 'pack.mcmeta':
                continue
            if relative.startswith('textures/blocks/'):
                for pack in ('default', 'original'):
                    destination = TEXTURES / pack / 'assets/vandorlabs' / relative
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    destination.write_bytes(source.read(name))
            elif relative.startswith(('blockstates/', 'models/block/', 'models/item/')):
                destination = ASSETS / relative
                destination.parent.mkdir(parents=True, exist_ok=True)
                data = json.loads(source.read(name))
                encoded = json.dumps(data).replace('scifidoors:', 'vandorlabs:')
                destination.write_text(json.dumps(json.loads(encoded), indent=2) + '\n')
        language = source.read(PREFIX + 'lang/en_us.lang').decode()
        current = ROOT / 'generated-resources/assets/vandorlabs/lang/en_us.lang'
        lines = [line for line in current.read_text().splitlines()
                 if not any(line.startswith('tile.vandorlabs.' + old + '.') for old in OLD)
                 and not line.startswith('tile.vandorlabs.voxel_wall_')]
        lines.extend(language.replace('tile.scifidoors.', 'tile.vandorlabs.').splitlines())
        current.write_text('\n'.join(lines) + '\n')
        docs = ROOT / 'docs/voxel-wall-pack'
        docs.mkdir(parents=True, exist_ok=True)
        for name in ('README.md', 'AUTHORING.json', 'VALIDATION.json'):
            (docs / name).write_bytes(source.read('scifi_voxel_wall_pack/' + name))

    for old in OLD:
        (ASSETS / 'blockstates' / (old + '.json')).unlink(missing_ok=True)
        (ASSETS / 'models/block' / (old + '.obj')).unlink(missing_ok=True)
    (ASSETS / 'models/block/quiet_walls.mtl').unlink(missing_ok=True)
    for pack in ('default', 'original'):
        for suffix in ('.png', '.png.mcmeta'):
            (TEXTURES / pack / 'assets/vandorlabs/textures/blocks' /
             ('quiet_wall' + suffix)).unlink(missing_ok=True)
    ids = ['voxel_wall_' + shape + suffix
           for suffix in ('', '_bordered')
           for shape in ('regular', 'porthole', 'bottom_diagonal', 'top_diagonal')]
    models = ASSETS / 'dynmap-models.txt'
    lines = models.read_text().splitlines()
    at = next(i for i, line in enumerate(lines)
              if line.startswith(('modellist:id=%wall_regular,',
                                  'modellist:id=%voxel_wall_regular,')))
    lines = [line for line in lines if not any(line.startswith('modellist:id=%' + old + ',') for old in OLD)
             and not any(line.startswith('modellist:id=%' + ident + ',') for ident in ids)]
    additions = []
    for ident in ids:
        for facing, angle in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            if 'diagonal' in ident:
                parts = []
                for step in range(9):
                    lo, hi = max(0, 2*step-1), min(16, 2*step+1)
                    y0, y1 = (lo, hi) if 'bottom' in ident else (16-hi, 16-lo)
                    parts.append((y0, y1, step, step+6))
            else:
                parts = [(0, 16, 8, 14)]
            boxes = []
            for y0, y1, z0, z1 in parts:
                box = f'box=0/{y0}/{z0}:16/{y1}/{z1}:e/0:u/0:n/0:s/0:d/0:w/0'
                boxes.append(box + (f':R/0/{angle}/0' if angle else ''))
            additions.append(f'modellist:id=%{ident},state=facing:{facing},' + ','.join(boxes))
    lines[at:at] = additions
    models.write_text('\n'.join(lines) + '\n')

    textures = ASSETS / 'dynmap-texture.txt'
    lines = textures.read_text().splitlines()
    at = next(i for i, line in enumerate(lines)
              if line.startswith(('texture:id=quiet_wall,', 'texture:id=voxel_wall,')))
    lines = [line for line in lines if not line.startswith('texture:id=quiet_wall,')
             and not any(line.startswith('block:id=%' + old + ',') for old in OLD)
             and not any(line.startswith('block:id=%' + ident + ',') for ident in ids)
             and not line.startswith('texture:id=voxel_wall,')
             and not line.startswith('texture:id=voxel_wall_bordered,')]
    additions = [f'texture:id={name},filename=assets/vandorlabs/textures/blocks/{name}.png,xcount=1,ycount=1'
                 for name in ('voxel_wall', 'voxel_wall_bordered')]
    for ident in ids:
        texture = 'voxel_wall_bordered' if ident.endswith('_bordered') else 'voxel_wall'
        for facing in ('north', 'east', 'south', 'west'):
            additions.append(f'block:id=%{ident},state=facing:{facing},transparency=SEMITRANSPARENT,stdrot=true,patch0=0:{texture}')
    lines[at:at] = additions
    textures.write_text('\n'.join(lines) + '\n')
    print('Installed eight voxel walls and removed four retired wall assets.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    main(parser.parse_args().archive)
