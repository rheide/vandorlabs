"""Import company armor into the existing programmable armor choice catalog."""
import argparse
import json
import shutil
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
CATALOG=ROOT/'generated-resources/assets/vandorlabs/data/armor_textures.json'
TEXTURES=ROOT/'texture-packs/additional/assets/vandorlabs/textures'
MODELS=ROOT/'src/main/resources/assets/vandorlabs/models/item/armor'
COMPANIES=('pinnacle','ashiro','paladyne','vektor','seyr','vandor')
PARTS={'helmet':('HEAD','Helmet'),'chestplate':('CHEST','Chestplate'),
       'leggings':('LEGS','Leggings'),'boots':('FEET','Boots')}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory',type=Path)
    source=parser.parse_args().directory
    entries=json.loads(CATALOG.read_text())
    known={entry['name']:entry for entry in entries}
    next_id=max(entry['choice'] for entry in entries)+1
    for company in COMPANIES:
        pack,namespace=('AshiroArmor_Forge_1.12.2','ashiroarmor') if company=='ashiro' else ('CorporateArmor_Forge_1.12.2','corporatearmor')
        assets=source/pack/'src/main/resources/assets'/namespace/'textures'
        worn=TEXTURES/'models/armor/companies'
        worn.mkdir(parents=True,exist_ok=True)
        for layer in (1,2):
            name=f'{company}_layer_{layer}.png'
            path=assets/'models/armor'/name
            with Image.open(path) as image:
                assert image.size==(64,32),path
            shutil.copyfile(path,worn/name)
        for part,(slot,label) in PARTS.items():
            name=f'{company}_{part}'
            entry=known.get(name)
            if entry is None:
                entry={'choice':next_id,'name':name,'slot':slot,'label':company.title()+' '+label,
                       'role':company,'variant':'full',
                       'worn':f'vandorlabs:textures/models/armor/companies/{company}_layer_{2 if slot=="LEGS" else 1}.png',
                       'icon':f'vandorlabs:items/armor/{name}','model':f'vandorlabs:armor/{name}'}
                entries.append(entry);known[name]=entry;next_id+=1
            icon=Image.open(assets/'items'/(name+'.png')).convert('RGBA')
            assert icon.size==(16,16),name
            assert not any(icon.getchannel('A').histogram()[1:255]),name
            x0,y0,x1,y1=icon.getbbox()
            if x0<1 or y0<1 or x1>15 or y1>15:
                art=icon.crop((x0,y0,x1,y1));art.thumbnail((14,14),Image.Resampling.NEAREST)
                icon=Image.new('RGBA',(16,16));icon.paste(art,((16-art.width)//2,(16-art.height)//2))
            target=TEXTURES/'items/armor'/(name+'.png');target.parent.mkdir(parents=True,exist_ok=True)
            icon.save(target,optimize=True)
            model={'parent':'item/generated','textures':{'layer0':entry['icon']},
                   'display':{'gui':{'rotation':[0,0,0],'scale':[.85,.85,.85]}}}
            MODELS.mkdir(parents=True,exist_ok=True)
            (MODELS/(name+'.json')).write_text(json.dumps(model,indent=2)+'\n')
    CATALOG.write_text(json.dumps(entries,indent=2)+'\n')
    print(f'Imported six company sets, 24 slot choices; catalog has {len(entries)} stable choices')


if __name__=='__main__':main()
