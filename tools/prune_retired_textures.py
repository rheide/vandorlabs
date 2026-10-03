#!/usr/bin/env python3
"""Remove retired picker artwork from the staged runtime assets, preserving aliases."""
import argparse
import json
import re
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
FALLBACK = 'vandorlabs:blocks/dark_wall_panel'


def retired():
    menu = json.loads((ROOT / 'src/main/resources/assets/vandorlabs/data/texture_menu.json').read_text())
    java = (ROOT / 'src/main/java/com/vandorlabs/tiles/ScreenHousingTextures.java').read_text()
    finishes = re.findall(r'new Finish\("([^"]+)", "([^"]+)"\)', java)
    entries = json.loads((ROOT / 'generated-resources/assets/vandorlabs/data/unified_textures.json').read_text())
    unused = {path for name, path in finishes if menu.get(name, {}).get('hidden')}
    used = {path for name, path in finishes if not menu.get(name, {}).get('hidden')}
    for entry in entries:
        paths = {entry['source']}
        paths.update(entry[key] for key in ('unlit', 'top', 'side') if key in entry)
        if entry.get('rectangular'):
            paths.add('unified/' + entry['id'])
            if 'design' in entry:
                paths.add('unified/' + entry['id'] + '_half')
        (unused if entry.get('hidden') else used).update(paths)
    return unused - used


def remap_model(text, paths):
    model = json.loads(text)
    changed = False
    for key, value in model.get('textures', {}).items():
        if value in {'vandorlabs:blocks/' + path for path in paths}:
            model['textures'][key] = FALLBACK
            changed = True
    return json.dumps(model, indent=2) + '\n' if changed else text


def stage(directory):
    paths = retired()
    removed = 0
    saved = 0
    for path in paths:
        for suffix in ('.png', '.png.mcmeta'):
            file = directory / 'textures/blocks' / (path + suffix)
            if file.exists():
                saved += file.stat().st_size
                file.unlink()
                removed += 1
    remapped = 0
    for file in (directory / 'models').rglob('*.json'):
        before = file.read_text()
        after = remap_model(before, paths)
        if after != before:
            file.write_text(after)
            remapped += 1
    # Preserve Dynmap's old texture identifiers while retiring their image files.
    for file in directory.glob('dynmap*.txt'):
        text = file.read_text()
        for path in paths:
            text = text.replace('filename=blocks/' + path + '.png', 'filename=blocks/dark_wall_panel.png')
        file.write_text(text)
    print(f'Pruned {removed} retired texture files ({saved} source bytes); remapped {remapped} compatibility models')


def check_jar(file):
    paths = retired()
    prefix = 'assets/vandorlabs/'
    with ZipFile(file) as archive:
        names = set(archive.namelist())
        for path in paths:
            assert prefix + 'textures/blocks/' + path + '.png' not in names, path
            assert prefix + 'textures/blocks/' + path + '.png.mcmeta' not in names, path
        assert prefix + 'textures/blocks/dark_wall_panel.png' in names
        for name in names:
            if name.startswith(prefix + 'models/') and name.endswith('.json'):
                before = archive.read(name).decode()
                assert remap_model(before, paths) == before, f'retired texture reference: {name}'
            if name.startswith(prefix + 'dynmap') and name.endswith('.txt'):
                text = archive.read(name).decode()
                for path in paths:
                    assert 'filename=blocks/' + path + '.png' not in text, f'retired map texture: {path}'
        # Required non-picker surfaces and every visible catalog source remain.
        for path in ('programmable_glass/metal_side', 'porthole_on', 'porthole_off'):
            assert prefix + 'textures/blocks/' + path + '.png' in names, path
        entries = json.loads(archive.read(prefix + 'data/unified_textures.json'))
        for entry in entries:
            if not entry.get('hidden'):
                for key in ('source', 'side', 'top', 'unlit'):
                    if key in entry:
                        assert prefix + 'textures/blocks/' + entry[key] + '.png' in names, entry[key]
    print(f'PASS: {len(paths)} retired texture paths absent; compatibility models and required assets intact')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stage', type=Path)
    parser.add_argument('--check-jar', type=Path)
    args = parser.parse_args()
    if args.stage:
        stage(args.stage)
    if args.check_jar:
        check_jar(args.check_jar)
    if not args.stage and not args.check_jar:
        parser.error('provide --stage or --check-jar')
