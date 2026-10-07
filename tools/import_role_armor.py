#!/usr/bin/env python3
"""Import native armor atlases and icons without modifying their PNG bytes."""
import argparse
import hashlib
import io
import json
from pathlib import Path
from zipfile import ZipFile
from contextlib import ExitStack
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'texture-packs/additional/assets/vandorlabs/textures'
MODELS = ROOT / 'src/main/resources/assets/vandorlabs/models/item/armor'
CATALOG = ROOT / 'generated-resources/assets/vandorlabs/data/armor_textures.json'
PARTS = {'helmet': ('HEAD', 'Helmet'), 'chestpiece': ('CHEST', 'Chestplate'),
         'leggings': ('LEGS', 'Leggings'), 'boots': ('FEET', 'Boots')}
ALTERNATES = {"helmet_open": ("HEAD", "Helmet (Open)"),
              "chestpiece_short": ("CHEST", "Chestplate (Short Sleeves)")}
ID_BASE = 0x10000000


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path, help='ZIP archive or extracted pack directory')
    parser.add_argument('--role', action='append', help='Import only this role; may repeat')
    parser.add_argument("--alternates-only", action="store_true", help="Append open helmets and short-sleeve chestpieces without replacing original artwork")
    args = parser.parse_args()
    entries = json.loads(CATALOG.read_text()) if CATALOG.exists() else []
    existing = {entry['name']: entry for entry in entries}
    next_id = max([ID_BASE - 1] + [entry['choice'] for entry in entries]) + 1
    with ExitStack() as stack:
        if args.archive.is_dir():
            read = lambda name: (args.archive / name).read_bytes()
        else:
            read = stack.enter_context(ZipFile(args.archive)).read
        checksums = dict(line.split('  ', 1)[::-1] for line in
                         read('SHA256SUMS.txt').decode().splitlines() if line)
        roles = list(dict.fromkeys(entry['role'] for entry in
                                   json.loads(read('texture_index.json'))))
        if args.role:
            unknown = set(args.role) - set(roles)
            if unknown:
                parser.error('Unknown roles: ' + ', '.join(sorted(unknown)))
            roles = [role for role in roles if role in args.role]
        for role in roles:
            for part, (slot, label) in (ALTERNATES if args.alternates_only else PARTS).items():
                name = f'{role}_{part}'
                if name not in existing:
                    entry = {'choice': next_id, 'name': name, 'slot': slot,
                             'label': role.replace('_', ' ').title() + ' ' + label,
                             'worn': f'vandorlabs:textures/models/armor/roles/{name}.png',
                             'icon': f'vandorlabs:items/armor/{name}',
                             'model': f'vandorlabs:armor/{name}'}
                    if args.alternates_only:
                        entry.update(role=role, variant="open")
                    entries.append(entry)
                    existing[name] = entry
                    next_id += 1
                for source_dir, target_dir, expected_size in [
                        ('models/armor', 'models/armor/roles', (64, 32)),
                        ('items', 'items/armor', (16, 16))]:
                    source = f'assets/mctrek_armor/textures/{source_dir}/{name}.png'
                    data = read(source)
                    assert hashlib.sha256(data).hexdigest() == checksums[source], source
                    image = Image.open(io.BytesIO(data)).convert('RGBA')
                    assert image.size == expected_size, source
                    assert set(image.getchannel('A').get_flattened_data()) <= {0, 255}, source
                    if source_dir == 'items':
                        x0, y0, x1, y1 = image.getbbox()
                        assert x0 > 0 and y0 > 0 and x1 < 16 and y1 < 16, source
                    target = ASSETS / target_dir / f'{name}.png'
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(data)
                model = {'parent': 'item/generated',
                         'textures': {'layer0': existing[name]['icon']},
                         'display': {'gui': {'rotation': [0, 0, 0], 'scale': [.85, .85, .85]}}}
                MODELS.mkdir(parents=True, exist_ok=True)
                (MODELS / f'{name}.json').write_text(json.dumps(model, indent=2) + '\n')
    CATALOG.write_text(json.dumps(entries, indent=2) + '\n')
    print(f'Imported {len(roles)} roles, {len(entries)} slot-specific choices; PNG bytes preserved')


if __name__ == '__main__':
    main()
