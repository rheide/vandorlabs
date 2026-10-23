"""Validate fourteen closed token cuboids and standard-resolution artwork."""
import json
from pathlib import Path
from PIL import Image
root=Path(__file__).resolve().parents[1]
for kind in ('card','chip'):
    textures=root/'texture-packs/additional/assets/vandorlabs/textures/items'
    for name in ('unbranded','pinnacle','ashiro','paladyne','vektor','seyr','vandor'):
        identifier=('access_card' if kind=='card' else 'data_chip')+'_'+name
        art=Image.open(textures/(identifier+'.png')).convert('RGBA')
        assert art.size==(128,128) and sum(a==0 for a in art.getchannel('A').getdata())==4,identifier
        model=json.loads((root/'src/main/resources/assets/vandorlabs/models/item'/(identifier+'.json')).read_text())
        assert len(model['elements'])==3,identifier
        element=model['elements'][0]
        assert set(element['faces'])=={'north','south','east','west','up','down'},identifier
        assert element['to'][2]-element['from'][2]==1,identifier
        width=max(e['to'][0] for e in model['elements'])-min(e['from'][0] for e in model['elements']);height=max(e['to'][1] for e in model['elements'])-min(e['from'][1] for e in model['elements'])
        assert (width<height if kind=='card' else width==height),identifier
        for face in ('east','west','up','down'):assert element['faces'][face]['texture']=='#edge',identifier
        for face in ('north','south'):assert element['faces'][face]['texture']=='#front',identifier
print('PASS: fourteen closed corner-clipped props; rectangular cards, square chips, one-unit depth and 128px artwork with four clipped corner pixels')
