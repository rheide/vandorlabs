"""Check company layer sizes, alpha, timing and append-only catalog ownership."""
import json
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/'generated-resources/assets/vandorlabs/data'
TEXTURES=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks'
catalog=json.loads((DATA/'unified_textures.json').read_text())
frames=[e for e in catalog if e['id'].startswith('frame_company_')]
screens=[e for e in catalog if e['id'].startswith('screen_company_')]
assert len(frames)==12 and len(screens)==72
assert len({e['id'] for e in catalog})==len(catalog)
for entry in frames:
    image=Image.open(TEXTURES/(entry['source']+'.png')).convert('RGBA')
    assert image.size==(64,64)
    if entry['frameAspect']==2:
        assert image.crop((0,32,64,64)).getchannel('A').getextrema()==(0,0)
    assert Image.open(TEXTURES/(entry['frameRail']+'.png')).size==(64,64)
for entry in screens:
    source=entry['source'];size=(128,64 if source.startswith('console_inputs/') else 128)
    first=Image.open(TEXTURES/(source+'.png')).convert('RGBA')
    stripPath=TEXTURES/(source.removesuffix('_static')+'_animated.png')
    strip=Image.open(stripPath).convert('RGBA')
    assert first.size==size and strip.size==(size[0],size[1]*6)
    if source.startswith('console_inputs/'):
        off=Image.open(TEXTURES/(source.removesuffix('_static')+'_off.png')).convert('RGBA')
        assert off.size==size and off.getchannel('A').tobytes()==first.getchannel('A').tobytes()
        assert off.convert('RGB').getbbox() is None,'OFF layer is not black'
    assert strip.crop((0,0,*size)).tobytes()==first.tobytes()
    assert strip.getchannel('A').getextrema()[0]<255,'Layer alpha was flattened'
    metadata=json.loads(Path(str(stripPath)+'.mcmeta').read_text())['animation']
    assert metadata==dict(width=size[0],height=size[1],frametime=4,frames=list(range(6)))
    assert entry['menuGroup'].startswith('Company/') and entry['category']=='Screens'
print('company-screen-layers PASS borders=12 displays=72 frames=432 native sizes/alpha/four-tick timing')
