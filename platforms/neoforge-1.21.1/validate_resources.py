#!/usr/bin/env python3
"""Validate packaged modern resources and canonical catalog references."""
import json
import sys
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile
from PIL import Image


def validate(jar):
    with ZipFile(jar) as archive:
        names=set(archive.namelist())
        assert 'META-INF/neoforge.mods.toml' in names
        assert 'mcmod.info' not in names
        assert not any('/recipes/' in name for name in names)
        language=json.loads(archive.read('assets/vandorlabs/lang/en_us.json'))
        root=Path(__file__).resolve().parents[2]
        for line in (root/'generated-resources/assets/vandorlabs/lang/en_us.lang').read_text().splitlines():
            if '=' not in line or line.startswith('#'):continue
            key,value=line.split('=',1)
            if key.startswith('tile.vandorlabs.'):key=key.replace('tile.vandorlabs.','block.vandorlabs.',1).removesuffix('.name')
            elif key.startswith('item.vandorlabs.'):key=key.removesuffix('.name')
            assert language.get(key)==value,('canonical language drift',key,value,language.get(key))
        catalog=json.loads(archive.read('assets/vandorlabs/data/housing_finishes.json'))
        assert len(catalog)>400 and len({e['id'] for e in catalog})==len(catalog)
        for i,entry in enumerate(catalog):
            assert 0<=entry['canonical']<len(catalog),(i,entry)
            namespace,path=entry['texture'].split(':')
            filename=f'assets/{namespace}/textures/{path}.png'
            assert filename in names,filename
            with Image.open(BytesIO(archive.read(filename))) as image:
                w,h=image.size
                assert w==h or filename+'.mcmeta' in names,(filename,w,h)
                animated=filename+'.mcmeta' in names and 'animation' in json.loads(archive.read(filename+'.mcmeta'))
                assert max(w,h)<=128 or animated,(filename,'oversized housing texture',w,h)
            assert 0<entry['u']<=1 and 0<entry['v']<=1,entry
        for filename in names:
            if filename.startswith('assets/vandorlabs/textures/item/') and filename.endswith('.png'):
                with Image.open(BytesIO(archive.read(filename))) as image:
                    assert image.size==(128,128),(filename,'native item artwork must match legacy packed128px',image.size)
        atlas=json.loads(archive.read('assets/minecraft/atlases/blocks.json'))
        singles={entry.get('sprite',entry['resource']) for entry in atlas['sources'] if entry['type']=='minecraft:single'}
        for filename in names:
            if not filename.startswith('assets/vandorlabs/models/') or not filename.endswith('.json'):
                continue
            model=json.loads(archive.read(filename))
            for texture in model.get('textures',{}).values():
                if texture.startswith('#'):
                    continue
                namespace,path=texture.split(':') if ':' in texture else ('minecraft',texture)
                if namespace!='vandorlabs':
                    continue
                target=f'assets/{namespace}/textures/{path}.png'
                assert target in names,(filename,target)
                assert path.startswith(('block/','item/')) or texture in singles,(filename,'texture not in block atlas',texture)
        for id in ('programmable_block','programmable_viewscreen'):
            for filename in (f'assets/vandorlabs/blockstates/{id}.json',f'assets/vandorlabs/models/item/{id}.json',f'data/vandorlabs/loot_table/blocks/{id}.json'):
                assert filename in names,filename
                json.loads(archive.read(filename))
        screens=json.loads(archive.read('assets/vandorlabs/data/screen_artwork.json'))
        assert screens
        for entry in screens:
            for variant in ('bare','framed'):
                assert f'assets/vandorlabs/textures/screens/{entry[variant]}_static.png' in names,entry
        from validate_control_resources import validate as validate_controls
        validate_controls(root, archive)
        print(f'Modern resource contracts PASS: {len(catalog)} stable housing choices, {len(screens)} paired screens; modern metadata, models, loot and sprites')

if __name__=='__main__':
    validate(Path(sys.argv[1]))
