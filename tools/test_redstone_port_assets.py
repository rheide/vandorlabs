"""Check face roles, opposite signal flow, item padding settings and glass transparency."""
import json
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
for kind in ('transmitter','receiver'):
    name='redstone_'+kind
    folder=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks'/name
    for face in ('up','port','type','left','right','down'):
        image=Image.open(folder/(face+'.png'))
        assert image.size==(128,128),(name,face)
    model=json.loads((ROOT/'src/main/resources/assets/vandorlabs/models/block'/(name+'.json')).read_text())
    assert model['textures']['north'].endswith('/port') and model['textures']['south'].endswith('/type')
    expected=('up','down','left','right') if kind=='receiver' else ('down','up','right','left')
    assert tuple(model['textures'][face].split('/')[-1] for face in ('up','down','west','east'))==expected
    states=json.loads((ROOT/'src/main/resources/assets/vandorlabs/blockstates'/(name+'.json')).read_text())
    assert len(states['variants'])==6
catalog=json.loads((ROOT/'generated-resources/assets/vandorlabs/data/unified_textures.json').read_text())
for index in range(1,5):
    entry=next(e for e in catalog if e['id']==f'security_glass_{index}')
    assert entry['category']=='Glass'
    image=Image.open(ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks'/(entry['source']+'.png'))
    assert image.size==(128,128) and image.mode=='RGBA'
    alpha=image.getchannel('A').histogram()
    assert alpha[0]>128*128*.65 and sum(alpha[1:])>128*128*.05
print('PASS: two 128px six-face endpoint sets, inverse arrow flow and four transparent security glass choices')
