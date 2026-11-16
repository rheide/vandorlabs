#!/usr/bin/env python3
"""Check native Infuser appearance, orientation, source recipe and exact artwork before client tests."""
import hashlib,json
from pathlib import Path
from PIL import Image
from generate_infuser_resources import ROOT,OUT,SOURCE,ID

def read(relative):return json.loads((OUT/relative).read_text())
def main():
    assets=OUT/'assets/vandorlabs';manifest=read('assets/vandorlabs/data/native_infuser.json')
    assert set(manifest['textures'])=={'vandorlabs:block/native_infuser/'+name for name in ('casing','lens','front','core')}
    for reference,entry in manifest['textures'].items():
        source=ROOT/'texture-packs/additional/assets/vandorlabs/textures'/(entry['source'].split(':',1)[1]+'.png');target=assets/'textures'/(reference.split(':',1)[1]+'.png')
        assert source.read_bytes()==target.read_bytes() and hashlib.sha256(target.read_bytes()).hexdigest()==entry['sha256']
        with Image.open(target) as image:assert list(image.size)==entry['size']==[64,64]
    original=json.loads((SOURCE/f'models/block/{ID}.json').read_text());block=read(f'assets/vandorlabs/models/block/{ID}.json')
    assert block['parent']=='minecraft:block/cube' and block['elements']==original['elements']
    assert block['textures']=={key:'vandorlabs:block/native_infuser/'+value.rsplit('/',1)[1] for key,value in original['textures'].items()}
    states=read(f'assets/vandorlabs/blockstates/{ID}.json')['variants'];assert set(states)=={'facing='+side for side in ('north','east','south','west')}
    for side,angle in zip(('north','east','south','west'),(0,90,180,270)):assert states['facing='+side]=={'model':'vandorlabs:block/'+ID,'y':angle}
    item=read(f'assets/vandorlabs/models/item/{ID}.json');assert item==json.loads((SOURCE/f'models/item/{ID}.json').read_text()) and max(item['display']['gui']['scale'])==.5
    core=read('assets/vandorlabs/models/item/infuser_core.json');assert core['parent']=='minecraft:item/generated' and core['textures']=={'layer0':'vandorlabs:block/native_infuser/core'} and max(core['display']['gui']['scale'])<1
    recipe=read(f'data/vandorlabs/recipe/{ID}.json');source=json.loads((SOURCE/f'recipes/{ID}.json').read_text())
    assert recipe['type']==source['type'] and recipe['pattern']==source['pattern'] and recipe['key']==source['key']
    assert recipe['result']=={'id':'vandorlabs:'+ID,'count':1} and recipe['neoforge:conditions']==[{'type':'neoforge:item_exists','item':'vandorlabs:'+ID}]
    assert {p.name for p in (OUT/'data/vandorlabs/recipe').glob('*.json')}=={ID+'.json'}
    loot=read(f'data/vandorlabs/loot_table/blocks/{ID}.json');assert loot=={'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'vandorlabs:'+ID}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]}
    assert not list(OUT.rglob('atlases')) and not list(OUT.rglob('tags')) and not list(OUT.rglob('lang'))
    print('Native Infuser resources PASS fourfacing/native64px RGBA+bytes sourcecube+craftrecipe paddedBlockAndCoreIcons oneLoot noDuplicateAtlasTagsLang')
if __name__=='__main__':main()
