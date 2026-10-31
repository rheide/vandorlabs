"""Saved IDs, display-height taxonomy, controls separation and exact packaged artwork."""
import argparse,collections,hashlib,io,json,re,struct,zlib
from pathlib import Path
from zipfile import ZipFile
from PIL import Image
from restructure_screen_catalog import ROOT,restructure,CONTROL_PREFIXES,SEYR_CLASSIC,CLASSIC_KEYBOARDS,RETIRED_CONTROLS
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',type=Path);args=p.parse_args()
base=ROOT/'generated-resources/assets/vandorlabs';catalog=json.loads((base/'data/unified_textures.json').read_text());blocks=json.loads((base/'data/blocks.json').read_text());lang=(base/'lang/en_us.lang').read_text()
assert hashlib.sha256('\n'.join(e['id'] for e in catalog[:589]).encode()).hexdigest()=='eeafff4b2f8dd4bb611bef5d5beaf5ca03da0bfcc82a3159ab3cadd9e84f8466'
inputs=[e for e in catalog if e['source'].startswith(tuple('console_inputs/'+p for p in CONTROL_PREFIXES))]
assert len(inputs)==18 and hashlib.sha256(json.dumps([{k:v for k,v in e.items() if k not in {'label','category','menuGroup','hidden','alias','retireAnimation'}} for e in inputs],sort_keys=True).encode()).hexdigest()=='a4a027ab9cf2419c6641b9ecb619e0ba7e20af5a7735ffca89b22a8682501d3b'
assert restructure(catalog,blocks,lang)==(catalog,blocks,lang),'regeneration is not idempotent'
byid={e['id']:e for e in catalog};byblock={e['id']:e for e in blocks};groups=collections.defaultdict(list)
for e in catalog:
    if e.get('category','').startswith('Controls/') and not e.get('hidden'):
        assert not any(x in e['category'] for x in ('Corporate','Dense','Half-height')) and not re.match(r'^\d',e['label']) and not e['label'].endswith(' Bottom')
        groups[e['category']].append(e['label'].casefold())
assert len(groups)==7 and len(groups['Controls/Classic'])==16
for group,labels in groups.items():assert len(set(labels))==len(labels) and len(labels)==(16 if group=='Controls/Classic' else 10),group
for identifier,label in CLASSIC_KEYBOARDS.items():
    assert byid[identifier]['category']=='Controls/Classic' and byid[identifier]['label']==label and not byid[identifier].get('hidden')
for identifier in RETIRED_CONTROLS:assert byid[identifier]['hidden']
for group,company in [('Gold','pinnacle'),('Cool','paladyne')]:
    for height in ('Full','Half'):
        choices=[e for e in catalog if e.get('menuGroup')==group and not e.get('hidden') and ('console_inputs/' in e['source'])==(height=='Half')]
        assert len(choices)==6
        for e in choices:assert company in e['id'] and e['label'].endswith(f'({group}/{height})')
for e in catalog:
    if e.get('retireAnimation'):
        source=e['source'].removeprefix('console_inputs/').removesuffix('_static');assert e['hidden']
        if not e['source'].startswith('console_inputs/'):assert byblock[source]['retired']
        if source.startswith(('company_','bridge_','console_controls_')):assert 'alias' in e and not byid[e['alias']].get('hidden')
        else:assert 'alias' not in e
    if e.get('category')=='Screens' and not e.get('hidden') and e not in inputs:
        height='Half' if e['source'].startswith('console_inputs/') else 'Full';assert e['label'].endswith('/'+height+')') or e['label'].endswith('('+height+')'),e['id']
    if e.get('category')=='Screens' and e['source'].startswith(('ship_','cruiser_')):assert e['menuGroup']=='Ship'
for prefix in ('screen_','screen_console_inputs_'):
    for variant in ('bare','framed'):
        assert byid[f'{prefix}bridge_{variant}_sensors_static']['alias']==f'{prefix}specialist_{variant}_sensors_static'
        height='Half' if 'console_inputs' in prefix else 'Full';name='Radar';assert byid[f'{prefix}specialist_{variant}_sensors_static']['label']==f'{name} (Classic/{height})'
for tier in ('full','half'):
    for subject in SEYR_CLASSIC:
        e=byid[f'screen_company_seyr_{tier}_{subject}'];assert e['label']==subject.title()+f' (Classic/{tier.title()})' and not e.get('hidden')
# New half displays append choices; their authored native frames are compact and opaque.
new_half={}
for subject in ('station','targeting'):
    stem='analysis_bare_'+subject;identifier='screen_console_inputs_'+stem+'_static';entry=byid[identifier]
    assert entry['label']==subject.title()+' (Classic/Half)' and entry['menuGroup']=='Classic'
    assert catalog.index(entry)>=589 and entry['frames']==6 and entry['rectangular']
    assert dict(id=stem,animated=True,frames=6) in json.loads((base/'data/input_surfaces.json').read_text())
    relative='textures/blocks/console_inputs/'+stem
    source=ROOT/'texture-packs/additional/assets/vandorlabs'
    art=Image.open(source/(relative+'_static.png')).convert('RGB');encoded=(source/(relative+'_animated.anim')).read_bytes()
    assert encoded[:5]==b'VLTA\x02' and struct.unpack('>HHHI',encoded[5:15])==(128,64,6,128*64*3*5)
    delta=zlib.decompress(encoded[15:]);assert len(delta)==128*64*3*5
    raw=art.tobytes();frames=[raw]
    for i in range(5):
        raw=bytes((a+b)&255 for a,b in zip(raw,delta[i*128*64*3:(i+1)*128*64*3]));frames.append(raw)
    assert len(set(frames))==6 and art.size==(128,64)
    off=Image.open(source/(relative+'_off.png')).convert('RGBA');assert off.size==(128,64) and off.getextrema()==((0,0),(0,0),(0,0),(255,255))
    new_half[relative]=encoded
if args.jar:
    with ZipFile(args.jar) as jar:
        prefix='assets/vandorlabs/';names=set(jar.namelist());aliases=json.loads(jar.read(prefix+'data/texture_aliases.json'));assert json.loads(jar.read(prefix+'data/unified_textures.json'))==catalog
        for relative,encoded in new_half.items():assert jar.read(prefix+relative+'_animated.anim')==encoded
        for tier in ('full','half'):
            for subject in SEYR_CLASSIC:
                root=byid[f'screen_company_seyr_{tier}_{subject}']['source'].removesuffix('_static')
                for suffix in ('_static.png','_animated.png')+ (('_off.png',) if tier=='half' else ()):
                    relative='textures/blocks/'+root+suffix;packaged=Image.open(io.BytesIO(jar.read(prefix+aliases.get(relative,relative)))).convert('RGBA');original=Image.open(ROOT/'texture-packs/additional/assets/vandorlabs'/relative).convert('RGBA')
                    assert packaged.size==original.size and packaged.tobytes()==original.tobytes(),f'Seyr {tier} {subject} artwork changed'
                metadata='textures/blocks/'+root+'_animated.png.mcmeta';assert json.loads(jar.read(prefix+metadata))==json.loads((ROOT/'texture-packs/additional/assets/vandorlabs'/metadata).read_text())
        for e in catalog:
            if e.get('retireAnimation'):
                root='textures/blocks/'+e['source'].removesuffix('_static')
                for suffix in ('_static.png','_animated.png','_animated.anim','_animated.png.mcmeta')+(('_off.png',) if e['source'].startswith('console_inputs/') else ()):
                    assert prefix+root+suffix not in names and root+suffix not in aliases,(e['id'],suffix)
            elif not e.get('hidden'):
                path='textures/blocks/'+e['source']+'.png';assert prefix+aliases.get(path,path) in names,e['id']
    from prune_unused_runtime_assets import check_jar
    check_jar(args.jar)
print('Display/control catalog PASS: 589 saved indices; 18 native input identities retained; Gold/Cool both heights; six original Seyr animations; separate numbered-free unique company/Classic controls; retired assets absent')
