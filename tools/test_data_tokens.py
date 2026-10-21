"""Validate fourteen closed token cuboids and standard-resolution artwork."""
import json
from pathlib import Path
from PIL import Image
root=Path(__file__).resolve().parents[1]
for kind in ('card','chip'):
    textures=root/'texture-packs/additional/assets/vandorlabs/textures/items'
    for name in ('unbranded','pinnacle','ashiro','paladyne','vektor','seyr','vandor'):
        identifier=f'data_{kind}_{name}'
        art=Image.open(textures/(identifier+'.png')).convert('RGBA')
        assert art.size==(128,128) and art.getchannel('A').getextrema()==(255,255),identifier
        model=json.loads((root/'src/main/resources/assets/vandorlabs/models/item'/(identifier+'.json')).read_text())
        assert len(model['elements'])==1,identifier
        element=model['elements'][0]
        assert set(element['faces'])=={'north','south','east','west','up','down'},identifier
        assert element['to'][2]-element['from'][2]==1,identifier
        width=element['to'][0]-element['from'][0];height=element['to'][1]-element['from'][1]
        assert (width<height if kind=='card' else width==height),identifier
        for face in ('east','west','up','down'):assert element['faces'][face]['texture']=='#edge',identifier
        for face in ('north','south'):assert element['faces'][face]['texture']=='#front',identifier
print('PASS: fourteen closed cuboids; rectangular cards, square chips, one-unit depth and opaque 128px artwork')
