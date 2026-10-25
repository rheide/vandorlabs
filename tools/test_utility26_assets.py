"""Verify new custom-rendered programmer and coupler artwork in the standard JAR."""
import json, sys, io
from PIL import Image
from zipfile import ZipFile

with ZipFile(sys.argv[1]) as jar:
    names = set(jar.namelist())
    aliases = json.loads(jar.read('assets/vandorlabs/data/texture_aliases.json'))
    def resource_path(name):
        relative = name.removeprefix('assets/vandorlabs/')
        return 'assets/vandorlabs/' + aliases.get(relative, relative)

    models = [n for n in names if n.startswith('assets/vandorlabs/models/block/square_coupler') and n.endswith('.json')]
    materials = [n for n in names if n.startswith('assets/vandorlabs/models/block/data_chip_programmer/') and n.endswith('.mtl')]
    assert len(models) >= 5 and len(materials) >= 8, 'Missing native coupler/programmer models'
    references = set()
    for name in models:
        for value in json.loads(jar.read(name)).get('textures', {}).values():
            if value.startswith('vandorlabs:'):
                references.add('assets/vandorlabs/textures/' + value.split(':', 1)[1] + '.png')
    for name in materials:
        for line in jar.read(name).decode().splitlines():
            if line.startswith('map_Kd '):
                texture = line.split(maxsplit=1)[1]
                assert texture.startswith('vandorlabs:'), (name, texture)
                references.add('assets/vandorlabs/textures/' + texture.split(':', 1)[1].removesuffix('.png') + '.png')
    for name in sorted(references):
        assert resource_path(name) in names, 'Custom model artwork absent from standard JAR: ' + name
    for state in ('blank', 'ready', 'writing', 'complete'):
        prefix = f'assets/vandorlabs/models/block/data_chip_programmer/{state}/'
        assert 'usemtl screen_black' in jar.read(prefix + 'parts/screen.obj').decode()
        assert 'usemtl keyboard' in jar.read(prefix + 'parts/static.obj').decode()
    black = Image.open(io.BytesIO(jar.read(resource_path('assets/vandorlabs/textures/models/cockpits/blank.png')))).convert('RGBA')
    assert black.getextrema() == ((0, 0), (0, 0), (0, 0), (255, 255)), 'Programmer screen must be opaque black'
    keyboard = Image.open(io.BytesIO(jar.read('assets/vandorlabs/textures/blocks/data_chip_programmer/keyboard.png'))).convert('RGBA')
    assert keyboard.size == (128, 128), 'Native keyboard must use a square atlas sprite'
    from pathlib import Path
    standard_keyboard = Image.open(Path(__file__).resolve().parents[1] / 'texture-packs/default/assets/vandorlabs/textures/blocks/console_inputs/input_surfaces_keyboard_static.png').convert('RGBA')
    assert keyboard.crop((0, 0, 128, 64)).tobytes() == standard_keyboard.tobytes(), 'Programmer must reuse existing keyboard artwork without distortion'
    copy_model = json.loads(jar.read('assets/vandorlabs/models/block/copy_controller.json'))
    faces = {'front', 'back', 'left', 'right', 'top', 'bottom'}
    assert {value.rsplit('/', 1)[-1] for value in copy_model['textures'].values()} == faces
    for face in faces:
        name = f'assets/vandorlabs/textures/blocks/copy_controller/{face}.png'
        assert Image.open(io.BytesIO(jar.read(name))).size == (64, 64), name
    for texture in ('landing', 'landing-grey', 'maintenance', 'maintenance-grey', 'docking', 'docking-grey', 'copier-input', 'copier-output'):
        for suffix, size in (('', 512), ('_low', 64)):
            name = f'assets/vandorlabs/textures/blocks/surface/{texture}{suffix}.png'
            assert Image.open(io.BytesIO(jar.read(name))).size == (size, size), name
    for status in ('blank', 'ready', 'writing', 'complete'):
        assert f'assets/vandorlabs/textures/blocks/data_chip_programmer/programmer_{status}.png' in names
    print(f'Utility 2.6 packaged artwork PASS models={len(models)} material-files={len(materials)} texture-paths={len(references)}')
