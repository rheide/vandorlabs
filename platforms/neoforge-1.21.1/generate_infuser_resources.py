#!/usr/bin/env python3
"""Port only the Matter Infuser, its crafting dependency and exact released furnace artwork."""
import hashlib,json,shutil
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
OUT=Path(__file__).resolve().parent/'build/generated/infuser-resources'
SOURCE=ROOT/'src/main/resources/assets/vandorlabs'
ID='programmable_matter_infuser'

def main():
    if OUT.exists():shutil.rmtree(OUT)
    assets=OUT/'assets/vandorlabs';manifest={'textures':{}}
    def write(relative,value):
        target=OUT/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(value,indent=2)+'\n')
    # The existing native reservoir intentionally resamples shared casing to128px.
    # Infuser-local atlas paths preserve the released64px artwork without competing for that resource.
    def texture(name):
        old='vandorlabs:blocks/matter/'+name;new='vandorlabs:block/native_infuser/'+name
        source=ROOT/f'texture-packs/additional/assets/vandorlabs/textures/blocks/matter/{name}.png'
        with Image.open(source) as image:
            if image.width!=image.height or image.width>128:raise ValueError('Unexpected source Infuser artwork '+name)
            size=image.size
        target=assets/f'textures/block/native_infuser/{name}.png';target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
        metadata=source.with_suffix('.png.mcmeta')
        if metadata.exists():shutil.copyfile(metadata,target.with_suffix('.png.mcmeta'))
        manifest['textures'][new]={'source':old,'sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'size':list(size)}
        return new
    textures={f'vandorlabs:blocks/matter/{name}':texture(name) for name in ('casing','lens','front','core')}
    block=json.loads((SOURCE/f'models/block/{ID}.json').read_text());block['parent']='minecraft:block/cube';block['textures']={key:textures[value] for key,value in block['textures'].items()}
    write(f'assets/vandorlabs/models/block/{ID}.json',block)
    item=json.loads((SOURCE/f'models/item/{ID}.json').read_text());write(f'assets/vandorlabs/models/item/{ID}.json',item)
    core=json.loads((SOURCE/'models/item/infuser_core.json').read_text());core['parent']='minecraft:item/generated';core['textures']={key:textures[value] for key,value in core['textures'].items()}
    core.setdefault('display',{})['gui']={'scale':[.85,.85,.85]}
    write('assets/vandorlabs/models/item/infuser_core.json',core)
    states=json.loads((SOURCE/f'blockstates/{ID}.json').read_text())
    for variant in states['variants'].values():variant['model']=f'vandorlabs:block/{ID}'
    write(f'assets/vandorlabs/blockstates/{ID}.json',states)
    recipe=json.loads((SOURCE/f'recipes/{ID}.json').read_text());recipe['result']['id']=recipe['result'].pop('item');recipe['neoforge:conditions']=[{'type':'neoforge:item_exists','item':'vandorlabs:'+ID}]
    write(f'data/vandorlabs/recipe/{ID}.json',recipe)
    write(f'data/vandorlabs/loot_table/blocks/{ID}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'vandorlabs:'+ID}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    write('assets/vandorlabs/data/native_infuser.json',manifest)
    # A private standard block/ texture subtree needs no extra atlas source, lang or tags.
    owners=[folder for folder in OUT.parent.iterdir() if folder.is_dir() and folder!=OUT]
    owners.append(Path(__file__).resolve().parent/'src/main/resources')
    for path in OUT.rglob('*'):
        if path.is_file():
            for owner in owners:
                if (owner/path.relative_to(OUT)).is_file():raise ValueError('Unexpected duplicate Infuser resource '+str(path.relative_to(OUT)))
    print('Native Infuser resources: one directional machine, one dependency item, exact4 source sprites and source crafting pattern')
if __name__=='__main__':main()
