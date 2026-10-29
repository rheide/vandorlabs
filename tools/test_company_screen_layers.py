"""Check company layer sizes, alpha, timing and append-only catalog ownership."""
import argparse,json
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
for company in ('Ashiro','Paladyne','Pinnacle','Seyr','Vandor','Vektor'):
    group=[e for e in screens if e['label'].startswith(company+' ')]
    assert len(group)==12 and len({e['label'] for e in group})==12,company
    assert all(e['label'].split()[1] in ('Full','Half') for e in group),company
for entry in catalog:
    if entry['source'].startswith('frames/frames-screen-'):
        assert entry['label'].endswith(' Half' if '/frames-screen-half/' in entry['source'] else ' Full')

for entry in frames:
    image=Image.open(TEXTURES/(entry['source']+'.png')).convert('RGBA')
    assert image.size==(128,128)
    if entry['frameAspect']==2:
        assert image.crop((0,64,128,128)).getchannel('A').getextrema()==(0,0)
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
    assert not entry['menuGroup'].startswith('Company/') and entry['category']=='Screens'
print('company-screen-layers PASS borders=12 displays=72 frames=432 native sizes/alpha/four-tick timing')

# Optional source verification proves conversion identity, rather than inferring
# padding from a screenshot or merely checking dimensions of generated files.
parser=argparse.ArgumentParser();parser.add_argument('--source',type=Path);args=parser.parse_args()
if args.source:
    for entry in screens:
        identity=entry['id'].removeprefix('screen_company_')
        height='half' if '_half_' in identity else 'full'
        company,subject=identity.split('_'+height+'_',1)
        folder=args.source/'displays'/company/height/subject
        originals=[Image.open(folder/f'frame_{i:02d}.png').convert('RGBA') for i in range(6)]
        bounds=[im.getchannel('A').point(lambda a:255 if a>=128 else 0).getbbox() for im in originals]
        crop=(min(b[0] for b in bounds),min(b[1] for b in bounds),max(b[2] for b in bounds),max(b[3] for b in bounds))
        size=(128,64 if height=='half' else 128)
        actual=Image.open(TEXTURES/(entry['source'].removesuffix('_static')+'_animated.png')).convert('RGBA')
        for i,im in enumerate(originals):
            expected=im.crop(crop).resize(size,Image.Resampling.LANCZOS)
            assert actual.crop((0,i*size[1],size[0],(i+1)*size[1])).tobytes()==expected.tobytes(),(entry['id'],i,'conversion changed authored pixels')
        if entry['id']=='screen_company_vektor_full_communications':print('Vektor communications exact source',originals[0].size,'stable six-frame crop',crop,'runtime',size,'pixel parity PASS')
    print('company-source-conversion PASS exact six-frame crop/resize parity=432')
