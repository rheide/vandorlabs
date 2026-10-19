"""Check opaque, closed card/chip silhouettes and standard-resolution artwork."""
import json
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1]
models = root / 'src/main/resources/assets/vandorlabs/models/item'
textures = root / 'texture-packs/additional/assets/vandorlabs/textures/items'
for kind in ('card', 'chip'):
    edge = Image.open(textures / f'data_{kind}_edge.png').convert('RGBA')
    assert edge.getchannel('A').getextrema() == (255, 255), kind
    for name in ('unbranded', 'pinnacle', 'ashiro', 'paladyne', 'vektor', 'seyr', 'vandor'):
        identifier = f'data_{kind}_{name}'
        art = Image.open(textures / (identifier + '.png')).convert('RGBA')
        assert art.size == (128, 128), identifier
        assert not any(art.getchannel('A').histogram()[1:255]), identifier
        expected = {(x, y) for y in range(128) for x in range(128) if art.getpixel((x, y))[3]}
        model = json.loads((models / (identifier + '.json')).read_text())
        actual = set()
        for element in model['elements']:
            assert set(element['faces']) == {'north', 'south', 'east', 'west', 'up', 'down'}, identifier
            assert element['to'][2] - element['from'][2] == 2, identifier
            for face in ('east', 'west', 'up', 'down'):
                assert element['faces'][face]['texture'] == '#edge', identifier
            x0, x1 = (round(element[key][0] * 8) for key in ('from', 'to'))
            y0, y1 = 128 - round(element['to'][1] * 8), 128 - round(element['from'][1] * 8)
            patch = {(x, y) for y in range(y0, y1) for x in range(x0, x1)}
            assert not actual.intersection(patch), identifier
            actual.update(patch)
        assert actual == expected, identifier
print('PASS: fourteen closed card/chip meshes match opaque 128px silhouettes and solid side materials')
