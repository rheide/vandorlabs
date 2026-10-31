"""Saved IDs, display-height taxonomy, controls separation and exact packaged artwork."""
import argparse,collections,hashlib,io,json,re
from pathlib import Path
from zipfile import ZipFile
from PIL import Image
from restructure_screen_catalog import ROOT,restructure,CONTROL_PREFIXES,SEYR_CLASSIC
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',type=Path);args=p.parse_args()
base=ROOT/'generated-resources/assets/vandorlabs';catalog=json.loads((base/'data/unified_textures.json').read_text());blocks=json.loads((base/'data/blocks.json').read_text());lang=(base/'lang/en_us.lang').read_text()
assert hashlib.sha256('\n'.join(e['id'] for e in catalog[:589]).encode()).hexdigest()=='eeafff4b2f8dd4bb611bef5d5beaf5ca03da0bfcc82a3159ab3cadd9e84f8466'
inputs=[e for e in catalog if e.get('category')=='Screens' and e['source'].startswith(tuple('console_inputs/'+p for p in CONTROL_PREFIXES))]
assert len(inputs)==18 and hashlib.sha256(json.dumps(inputs,sort_keys=True).encode()).hexdigest()=='f2c8309280064349311bc775a107979a8f3f0f3352d549dfb7746f95d8e3a107'
assert restructure(catalog,blocks,lang)==(catalog,blocks,lang),'regeneration is not idempotent'
byid={e['id']:e for e in catalog};byblock={e['id']:e for e in blocks};groups=collections.defaultdict(list)
for e in catalog:
    if e.get('category','').startswith('Controls/'):
        assert not any(x in e['category'] for x in ('Corporate','Dense','Half-height')) and not re.match(r'^\d',e['label'])
        groups[e['category']].append(e['label'].casefold())
assert len(groups)==7 and len(groups['Controls/Classic'])==18
for group,labels in groups.items():assert len(set(labels))==len(labels) and len(labels)==(18 if group=='Controls/Classic' else 10),group
for group,company in [('Gold','pinnacle'),('Cool','paladyne')]:
    for height in ('Full','Half'):
        choices=[e for e in catalog if e.get('menuGroup')==group and not e.get('hidden') and ('console_inputs/' in e['source'])==(height=='Half')]
        assert len(choices)==6
        for e in choices:assert company in e['id'] and e['label'].endswith(f'({group}/{height})')
for e in catalog:
    if e.get('retireAnimation'):
        source=e['source'].removeprefix('console_inputs/').removesuffix('_static');assert e['hidden']
        if not e['source'].startswith('console_inputs/'):assert byblock[source]['retired']
        if source.startswith('company_') or source.startswith('bridge_'):assert 'alias' in e and not byid[e['alias']].get('hidden')
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
if args.jar:
    with ZipFile(args.jar) as jar:
        prefix='assets/vandorlabs/';names=set(jar.namelist());aliases=json.loads(jar.read(prefix+'data/texture_aliases.json'));assert json.loads(jar.read(prefix+'data/unified_textures.json'))==catalog
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
print('Display/control catalog PASS: 589 saved indices; 18 native inputs untouched; Gold/Cool both heights; six original Seyr animations; separate numbered-free unique company/Classic controls; retired assets absent')
