"""Verify new custom-rendered programmer and coupler artwork in the standard JAR."""
import json, sys
from zipfile import ZipFile

with ZipFile(sys.argv[1]) as jar:
    names = set(jar.namelist())
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
        assert name in names, 'Custom model artwork absent from standard JAR: ' + name
    for status in ('blank', 'ready', 'writing', 'complete'):
        assert f'assets/vandorlabs/textures/blocks/data_chip_programmer/programmer_{status}.png' in names
    print(f'Utility 2.6 packaged artwork PASS models={len(models)} material-files={len(materials)} texture-paths={len(references)}')
