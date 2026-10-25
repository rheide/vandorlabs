#!/usr/bin/env python3
"""Check glass finish opacity/color and projected padding for every inventory variant."""
import json
import math
from pathlib import Path
from PIL import Image, ImageStat

ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / 'texture-packs/additional/assets/vandorlabs/textures/blocks/programmable_glass'
for detail in ('low', 'medium'):
    clear = Image.open(TEXTURES / detail / 'clear.png').convert('RGBA')
    for file in (TEXTURES / detail).glob('*.png'):
        data = Image.open(file).convert('RGBA')
        assert data.size == clear.size
        if not file.stem.startswith(('clear', 'soft_cyan', 'soft_dark_grey')) and not file.stem.endswith(('_mild','_strong')):
            assert data.getchannel('A').getextrema()[0] >= 76, file.name
            assert ImageStat.Stat(data.getchannel('A')).mean[0] > ImageStat.Stat(clear.getchannel('A')).mean[0] + 40, file.name
    assert len(list((TEXTURES / detail).glob('*.png'))) == 57
    for name in ('soft_cyan','soft_dark_grey') + tuple(n for n in ('white','orange','magenta','light_blue','yellow','lime','pink','grey','light_grey','cyan','purple','blue','brown','green','red','black')):
        means = [ImageStat.Stat(Image.open(TEXTURES / detail / (name + suffix + '.png')).getchannel('A')).mean[0]
                 for suffix in ('_mild','','_strong')]
        assert means[0] < means[1] < means[2], (name, means)
    for suffix in ('_mild',):
        assert Image.open(TEXTURES / detail / ('clear'+suffix+'.png')).tobytes() == clear.tobytes()

    for file in (TEXTURES / detail).glob('*_strong.png'):
        assert Image.open(file).getchannel('A').getextrema()==(255,255), file

# Thickness caps sample the same fully opaque metal patch as regular glass.
metal = Image.open(ROOT / 'texture-packs/space-doors/medium/double_frame_metal.png').convert('RGBA')
solid = metal.crop((int(.16/16*metal.width), int(4/16*metal.width),
                   math.ceil(.28/16*metal.width), math.ceil(4.12/16*metal.width)))
assert solid.getchannel('A').getextrema() == (255,255), 'frame cap UV patch is not opaque'


def load(ref):
    path = ref.removeprefix('vandorlabs:') + '.json'
    for base in ('src/main/resources', 'generated-resources'):
        file = ROOT / base / 'assets/vandorlabs/models' / path
        if file.exists():
            model = json.loads(file.read_text())
            if model.get('parent', '').startswith('vandorlabs:'):
                parent = load(model['parent'])
                parent.update(model)
                return parent
            return model
    raise AssertionError(ref)


def rotate(point, axis, angle):
    a, b = {'x': (1, 2), 'y': (2, 0), 'z': (0, 1)}[axis]
    result = point.copy()
    c, s = math.cos(math.radians(angle)), math.sin(math.radians(angle))
    result[a], result[b] = c * point[a] - s * point[b], s * point[a] + c * point[b]
    return result


count = 0
for file in (ROOT / 'src/main/resources/assets/vandorlabs/models/item/configured').glob('glass_*.json'):
    model = load('vandorlabs:item/configured/' + file.stem)
    display = model['display']['gui']
    if file.stem.startswith('glass_diagonal_'):
        assert len(model['elements']) == 5, (file.name, 'missing visible inventory frame')
        assert model['textures']['frame'].endswith('/double_frame_metal')
        pane = model['elements'][0]
        assert pane['from'][:2] == [1,1] and pane['to'][:2] == [15,15]
        assert pane['from'][2] == 7 and pane['to'][2] == 9
        for rail in model['elements'][1:]:
            assert rail['from'][2] == 6 and rail['to'][2] == 10
            for side in ('east','west','up','down'):
                assert rail['faces'][side]['uv'] == [.16,4,.28,4.12], (file.name, side, 'transparent rim UV')
            assert all(face['texture'] == '#frame' for face in rail['faces'].values())
            # Rails touch the inset pane but never overlap its volume.
            assert any(rail['to'][axis] <= pane['from'][axis] or rail['from'][axis] >= pane['to'][axis]
                       for axis in range(3)), file.name
    for element in model['elements']:
        for x in (element['from'][0], element['to'][0]):
            for y in (element['from'][1], element['to'][1]):
                for z in (element['from'][2], element['to'][2]):
                    point = [float(x), float(y), float(z)]
                    if 'rotation' in element:
                        rotation = element['rotation']
                        origin = rotation['origin']
                        point = rotate([a-b for a,b in zip(point,origin)], rotation['axis'], rotation['angle'])
                        point = [a+b for a,b in zip(point,origin)]
                    point = [(a-8)*b for a,b in zip(point,display['scale'])]
                    for axis, angle in reversed(list(zip('xyz', display['rotation']))):
                        point = rotate(point, axis, angle)
                    assert abs(point[0]) <= 7.5 and abs(point[1]) <= 7.5, (file.name, point)
    count += 1
assert count == 399, count
print('PASS: 114 glass textures, three increasing tint strengths, and projected slot padding for all 399 item models')
